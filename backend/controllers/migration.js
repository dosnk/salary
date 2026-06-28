const sqlite3 = require('sqlite3').verbose();
const path = require('path');
const fs = require('fs');
const pool = require('../config/database');
const logger = require('../config/logger');

const SQLITE_DB_PATH = path.join(__dirname, '../scripts/xlmdata.db');

let migrationProgress = {
  total: 0,
  completed: 0,
  failed: 0,
  currentProject: null,
  status: 'idle',
  logs: []
};

const userMapping = {
  5: 5,
  6: 6,
  7: 7
};

const getMappedUserId = (oldUserId) => {
  return userMapping[oldUserId] || 7;
};

const getSpaceTypeId = async (spaceTypeName) => {
  try {
    const result = await pool.query('SELECT id FROM space_types WHERE name = $1', [spaceTypeName]);
    if (result.rows.length > 0) {
      return result.rows[0].id;
    }
    const insertResult = await pool.query('INSERT INTO space_types (name) VALUES ($1) RETURNING id', [spaceTypeName]);
    return insertResult.rows[0].id;
  } catch (error) {
    logger.error('获取或创建space_type失败:', error);
    return 1;
  }
};

const getConstructionPlanId = async (constructionPlanName) => {
  try {
    const result = await pool.query('SELECT id, unit, price FROM construction_plans WHERE name = $1', [constructionPlanName]);
    if (result.rows.length > 0) {
      return result.rows[0];
    }
    const insertResult = await pool.query('INSERT INTO construction_plans (name, unit, price) VALUES ($1, $2, $3) RETURNING id, unit, price', [constructionPlanName, 'length', 0]);
    return insertResult.rows[0];
  } catch (error) {
    logger.error('获取或创建construction_plan失败:', error);
    return { id: 1, unit: 'length', price: 0 };
  }
};

const getSqliteConnection = () => {
  return new Promise((resolve, reject) => {
    // 检查文件是否存在
    if (!fs.existsSync(SQLITE_DB_PATH)) {
      const error = new Error('数据库文件不存在，请先上传原始数据库文件');
      error.code = 'SQLITE_NO_FILE';
      reject(error);
      return;
    }
    
    // 检查文件大小
    const stats = fs.statSync(SQLITE_DB_PATH);
    if (stats.size === 0) {
      const error = new Error('数据库文件为空（0KB），可能已损坏，请重新上传原始数据库文件');
      error.code = 'SQLITE_EMPTY_FILE';
      reject(error);
      return;
    }
    
    // 使用只读模式打开，避免意外写入导致文件损坏
    const db = new sqlite3.Database(SQLITE_DB_PATH, sqlite3.OPEN_READONLY, (err) => {
      if (err) {
        // 根据错误类型返回友好提示
        let friendlyMessage = '数据库连接失败';
        if (err.code === 'SQLITE_CANTOPEN') {
          friendlyMessage = '无法打开数据库文件，请检查文件权限';
        } else if (err.code === 'SQLITE_NOTADB') {
          friendlyMessage = '文件不是有效的SQLite数据库，请检查文件格式';
        } else if (err.code === 'SQLITE_CORRUPT') {
          friendlyMessage = '数据库文件已损坏，请重新上传原始数据库文件';
        }
        
        const error = new Error(friendlyMessage);
        error.code = err.code || 'SQLITE_CONNECT_ERROR';
        reject(error);
      } else {
        resolve(db);
      }
    });
  });
};

const querySqlite = (db, sql, params = []) => {
  return new Promise((resolve, reject) => {
    db.all(sql, params, (err, rows) => {
      if (err) {
        reject(err);
      } else {
        resolve(rows);
      }
    });
  });
};

const addLog = (message, type = 'info') => {
  const timestamp = new Date().toLocaleString('zh-CN');
  migrationProgress.logs.unshift({ timestamp, message, type });
  if (migrationProgress.logs.length > 100) {
    migrationProgress.logs.pop();
  }
};

const login = async (ctx) => {
  const { username, password } = ctx.request.body;

  try {
    const result = await pool.query('SELECT * FROM users WHERE username = $1', [username]);
    if (result.rows.length === 0) {
      ctx.fail(2002, '用户名或密码错误');
      return;
    }

    const user = result.rows[0];

    const bcrypt = require('bcryptjs');
    const isMatch = await bcrypt.compare(password, user.password);
    if (!isMatch) {
      ctx.fail(2002, '用户名或密码错误');
      return;
    }

    const jwt = require('jsonwebtoken');
    const token = jwt.sign(
      { id: user.id, username: user.username, role: user.role, iat: Math.floor(Date.now() / 1000) },
      process.env.JWT_SECRET,
      { expiresIn: '24h' }
    );

    ctx.success({
      token,
      user: { id: user.id, username: user.username, nickname: user.nickname, role: user.role }
    });
  } catch (error) {
    logger.error('登录失败:', error);
    ctx.fail(5001, '登录失败');
  }
};

const getProjects = async (ctx) => {
  let db;
  try {
    db = await getSqliteConnection();
    const projects = await querySqlite(db, `
      SELECT 
        p.*,
        (SELECT COUNT(*) FROM project_items WHERE project_id = p.id) as item_count
      FROM projects p
      ORDER BY p.id DESC
    `);
    
    ctx.success(projects);
  } catch (error) {
    logger.error('获取工程列表失败:', error);
    ctx.fail(5001, error.message || '获取工程列表失败');
  } finally {
    if (db) db.close();
  }
};

const getProjectById = async (ctx) => {
  const projectId = ctx.params.id;
  let db;
  
  try {
    db = await getSqliteConnection();
    
    const projects = await querySqlite(db, 'SELECT * FROM projects WHERE id = ?', [projectId]);
    
    if (projects.length === 0) {
      ctx.fail(1002, '工程不存在');
      return;
    }
    
    const project = projects[0];
    
    const projectItems = await querySqlite(db, 'SELECT * FROM project_items WHERE project_id = ?', [projectId]);
    
    const attachments = await querySqlite(db, `
      SELECT * FROM attachments
      WHERE project_id = ?
      ORDER BY uploaded_at DESC
    `, [projectId]);
    
    ctx.success({
      project,
      projectItems,
      attachments
    });
  } catch (error) {
    logger.error('[ERROR] 获取工程详情失败:', error);
    ctx.fail(5001, error.message || '获取工程详情失败');
  } finally {
    if (db) db.close();
  }
};

const migrateProject = async (ctx) => {
  const { projectId } = ctx.request.body;
  let db;
  
  try {
    db = await getSqliteConnection();
    
    const projects = await querySqlite(db, 'SELECT * FROM projects WHERE id = ?', [projectId]);
    if (projects.length === 0) {
      ctx.fail(1002, '工程不存在');
      return;
    }
    
    const oldProject = projects[0];
    addLog(`开始迁移工程: ${oldProject.name}`, 'info');
    
    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      
      const projectResult = await client.query(`
        INSERT INTO projects (
          name, total_amount, salary_distribution, status, created_by, created_at, updated_at
        ) VALUES ($1, $2, $3, $4, $5, $6, $7)
        RETURNING id
      `, [
        oldProject.name || '',
        parseFloat(oldProject.total_amount) || 0,
        'average',
        'constructing',
        7,
        oldProject.created_at || new Date(),
        oldProject.updated_at || new Date()
      ]);
      
      const newProjectId = projectResult.rows[0].id;
      addLog(`工程创建成功: ${oldProject.name}, 新ID: ${newProjectId}`, 'success');
      
      const constructorList = [
        { userId: 5 },
        { userId: 6 },
        { userId: 7 }
      ];
      
      for (const constructor of constructorList) {
        await client.query(`
          INSERT INTO project_workers (project_id, user_id, created_at)
          VALUES ($1, $2, $3)
        `, [newProjectId, constructor.userId, new Date()]);
      }
      addLog(`添加施工人员: 5, 6, 7`, 'success');
      
      const projectItems = await querySqlite(db, 'SELECT * FROM project_items WHERE project_id = ?', [projectId]);
      for (const item of projectItems) {
        const spaceTypeId = await getSpaceTypeId(item.space_type || '客厅');
        const constructionPlan = await getConstructionPlanId(item.construction_plan || '半吊');
        
        const lengthM = (parseInt(item.length) || 0) / 100;
        const widthM = (parseInt(item.width) || 0) / 100;
        
        let quantity = 1;
        let amount = 0;
        
        if (constructionPlan.unit === 'length') {
          quantity = lengthM;
        } else if (constructionPlan.unit === 'perimeter') {
          quantity = 2 * (lengthM + widthM);
        } else if (constructionPlan.unit === 'area') {
          quantity = lengthM * widthM;
        }
        
        amount = quantity * (parseFloat(constructionPlan.price) || 0);
        
        await client.query(`
          INSERT INTO subprojects (
            project_id, space_type_id, construction_plan_id, length, width, quantity, 
            amount, remark, created_by, created_at, updated_at
          ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
        `, [
          newProjectId,
          spaceTypeId,
          constructionPlan.id,
          lengthM,
          widthM,
          quantity,
          amount,
          item.note || '',
          7,
          item.created_at || new Date(),
          item.updated_at || new Date()
        ]);
        
        addLog(`子项目创建成功: ${item.space_type} - ${item.construction_plan} (长: ${item.length}cm → ${lengthM}m, 宽: ${item.width}cm → ${widthM}m, 数量: ${quantity}, 金额: ${amount.toFixed(2)})`, 'success');
      }
      
      // 创建工资分配记录
      const subprojectsResult = await client.query(`
        SELECT id, amount
        FROM subprojects
        WHERE project_id = $1
      `, [newProjectId]);
      
      const workersResult = await client.query(`
        SELECT user_id
        FROM project_workers
        WHERE project_id = $1
      `, [newProjectId]);
      
      const workerCount = workersResult.rows.length;
      
      // 如果没有施工人员，跳过工资分配记录创建
      if (workerCount === 0) {
        addLog(`工程没有施工人员，跳过工资分配记录创建`, 'warn');
      } else {
        for (const subproject of subprojectsResult.rows) {
          const amountPerWorker = subproject.amount / workerCount;
          
          for (const worker of workersResult.rows) {
            // 检查是否已存在工资分配记录
            const existingResult = await client.query(`
              SELECT id FROM wage_distributions
              WHERE subproject_id = $1 AND user_id = $2
            `, [subproject.id, worker.user_id]);
            
            if (existingResult.rows.length === 0) {
              // 只有不存在时才创建
              await client.query(`
                INSERT INTO wage_distributions (subproject_id, user_id, amount, workdays, created_at)
                  VALUES ($1, $2, $3, 1, $4)
              `, [
                subproject.id,
                worker.user_id,
                amountPerWorker,
                new Date()
              ]);
            }
          }
        }
        
        addLog(`工资分配记录创建完成: ${subprojectsResult.rows.length} 个子项目 × ${workerCount} 个施工人员`, 'success');
      }
      
      const attachments = await querySqlite(db, 'SELECT * FROM attachments WHERE project_id = ?', [projectId]);
      for (const attachment of attachments) {
        try {
          await client.query(`
            INSERT INTO files (
              project_id, filename, original_name, path, size, type, uploaded_by, created_at
            ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
          `, [
            newProjectId,
            attachment.name,
            attachment.name,
            attachment.path || '',
            attachment.size || 0,
            attachment.type || 'image',
            7,
            attachment.uploaded_at || new Date()
          ]);
          
          addLog(`附件记录创建成功: ${attachment.name} (文件路径: ${attachment.path})`, 'success');
        } catch (error) {
          addLog(`附件记录创建失败: ${attachment.name}, 错误: ${error.message}`, 'error');
          await client.query('ROLLBACK');
          throw error;
        }
      }
      
      await client.query('COMMIT');
      
      ctx.success({
        success: true,
        newProjectId,
        message: '迁移成功'
      });
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  } catch (error) {
    logger.error('迁移工程失败:', error);
    addLog(`迁移工程失败: ${error.message}`, 'error');
    ctx.fail(5001, `迁移失败: ${error.message}`);
  } finally {
    if (db) db.close();
  }
};

const migrateAll = async (ctx) => {
  let db;
  
  try {
    if (migrationProgress.status === 'running') {
      ctx.fail(4001, '迁移正在进行中');
      return;
    }
    
    db = await getSqliteConnection();
    const projects = await querySqlite(db, 'SELECT * FROM projects ORDER BY id');
    
    migrationProgress.total = projects.length;
    migrationProgress.completed = 0;
    migrationProgress.failed = 0;
    migrationProgress.status = 'running';
    migrationProgress.logs = [];
    
    addLog(`开始批量迁移，共 ${projects.length} 个工程`, 'info');
    
    for (const project of projects) {
      migrationProgress.currentProject = project.name;
      
      try {
        const response = await migrateProjectInternal(project.id);
        if (response.success) {
          migrationProgress.completed++;
          addLog(`工程迁移成功: ${project.name}`, 'success');
        } else {
          migrationProgress.failed++;
          addLog(`工程迁移失败: ${project.name}`, 'error');
        }
      } catch (error) {
        migrationProgress.failed++;
        addLog(`工程迁移失败: ${project.name}, 错误: ${error.message}`, 'error');
      }
    }
    
    migrationProgress.status = 'completed';
    migrationProgress.currentProject = null;
    addLog(`批量迁移完成，成功: ${migrationProgress.completed}, 失败: ${migrationProgress.failed}`, 'info');
    
    ctx.success({
      success: true,
      total: migrationProgress.total,
      completed: migrationProgress.completed,
      failed: migrationProgress.failed
    });
  } catch (error) {
    logger.error('批量迁移失败:', error);
    migrationProgress.status = 'error';
    addLog(`批量迁移失败: ${error.message}`, 'error');
    ctx.fail(5001, `批量迁移失败: ${error.message}`);
  } finally {
    if (db) db.close();
  }
};

const migrateProjectInternal = async (projectId) => {
  let db;
  
  try {
    db = await getSqliteConnection();
    
    const projects = await querySqlite(db, 'SELECT * FROM projects WHERE id = ?', [projectId]);
    if (projects.length === 0) {
      return { success: false, message: '工程不存在' };
    }
    
    const oldProject = projects[0];
    
    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      
      const projectResult = await client.query(`
        INSERT INTO projects (
          name, total_amount, salary_distribution, status, created_by, created_at, updated_at
        ) VALUES ($1, $2, $3, $4, $5, $6, $7)
        RETURNING id
      `, [
        oldProject.name || '',
        parseFloat(oldProject.total_amount) || 0,
        'average',
        'constructing',
        7,
        oldProject.created_at || new Date(),
        oldProject.updated_at || new Date()
      ]);
      
      const newProjectId = projectResult.rows[0].id;
      
      const constructorList = [
        { userId: 5 },
        { userId: 6 },
        { userId: 7 }
      ];
      
      for (const constructor of constructorList) {
        await client.query(`
          INSERT INTO project_workers (project_id, user_id, created_at)
          VALUES ($1, $2, $3)
        `, [newProjectId, constructor.userId, new Date()]);
      }
      
      const projectItems = await querySqlite(db, 'SELECT * FROM project_items WHERE project_id = ?', [projectId]);
      for (const item of projectItems) {
        const spaceTypeId = await getSpaceTypeId(item.space_type || '客厅');
        const constructionPlan = await getConstructionPlanId(item.construction_plan || '半吊');
        
        const lengthM = (parseInt(item.length) || 0) / 100;
        const widthM = (parseInt(item.width) || 0) / 100;
        
        let quantity = 1;
        let amount = 0;
        
        if (constructionPlan.unit === 'length') {
          quantity = lengthM;
        } else if (constructionPlan.unit === 'perimeter') {
          quantity = 2 * (lengthM + widthM);
        } else if (constructionPlan.unit === 'area') {
          quantity = lengthM * widthM;
        }
        
        amount = quantity * (parseFloat(constructionPlan.price) || 0);
        
        await client.query(`
          INSERT INTO subprojects (
            project_id, space_type_id, construction_plan_id, length, width, quantity, 
            amount, remark, created_by, created_at, updated_at
          ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
        `, [
          newProjectId,
          spaceTypeId,
          constructionPlan.id,
          lengthM,
          widthM,
          quantity,
          amount,
          item.note || '',
          7,
          item.created_at || new Date(),
          item.updated_at || new Date()
        ]);
      }
      
      // 创建工资分配记录
      const subprojectsResult = await client.query(`
        SELECT id, amount
        FROM subprojects
        WHERE project_id = $1
      `, [newProjectId]);
      
      const workersResult = await client.query(`
        SELECT user_id
        FROM project_workers
        WHERE project_id = $1
      `, [newProjectId]);
      
      const workerCount = workersResult.rows.length;
      
      // 如果没有施工人员，跳过工资分配记录创建
      if (workerCount === 0) {
        // 跳过工资分配记录创建
      } else {
        for (const subproject of subprojectsResult.rows) {
          const amountPerWorker = subproject.amount / workerCount;
          
          for (const worker of workersResult.rows) {
            // 检查是否已存在工资分配记录
            const existingResult = await client.query(`
              SELECT id FROM wage_distributions
              WHERE subproject_id = $1 AND user_id = $2
            `, [subproject.id, worker.user_id]);
            
            if (existingResult.rows.length === 0) {
              // 只有不存在时才创建
              await client.query(`
                INSERT INTO wage_distributions (subproject_id, user_id, amount, workdays, created_at)
                  VALUES ($1, $2, $3, 1, $4)
              `, [
                subproject.id,
                worker.user_id,
                amountPerWorker,
                new Date()
              ]);
            }
          }
        }
      }
      
      const attachments = await querySqlite(db, 'SELECT * FROM attachments WHERE project_id = ?', [projectId]);
      for (const attachment of attachments) {
        try {
          await client.query(`
            INSERT INTO files (
              project_id, filename, original_name, path, size, type, uploaded_by, created_at
            ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
          `, [
            newProjectId,
            attachment.name,
            attachment.name,
            attachment.path || '',
            attachment.size || 0,
            attachment.type || 'image',
            7,
            attachment.uploaded_at || new Date()
          ]);
        } catch (error) {
          logger.error(`附件记录创建失败: ${attachment.name}`, error);
        }
      }
      
      await client.query('COMMIT');
      
      return { success: true, newProjectId };
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  } catch (error) {
    logger.error('迁移工程失败:', error);
    return { success: false, message: error.message };
  } finally {
    if (db) db.close();
  }
};

const getProgress = async (ctx) => {
  ctx.success(migrationProgress);
};

const checkDatabase = async (ctx) => {
  let db;
  
  try {
    db = await getSqliteConnection();
    
    const tables = await querySqlite(db, `
      SELECT name FROM sqlite_master 
      WHERE type='table' AND name NOT LIKE 'sqlite_%'
      ORDER BY name
    `);
    
    const tableInfo = {};
    
    for (const table of tables) {
      const columns = await querySqlite(db, `PRAGMA table_info(${table.name})`);
      const count = await querySqlite(db, `SELECT COUNT(*) as count FROM ${table.name}`);
      
      tableInfo[table.name] = {
        columns: columns.map(col => ({
          name: col.name,
          type: col.type,
          notNull: col.notnull === 1,
          primaryKey: col.pk > 0,
          defaultValue: col.dflt_value
        })),
        recordCount: count[0].count
      };
    }
    
    const sampleProjects = await querySqlite(db, 'SELECT * FROM projects LIMIT 1');
    const sampleSubprojects = await querySqlite(db, 'SELECT * FROM subprojects LIMIT 1');
    
    ctx.success({
      databasePath: SQLITE_DB_PATH,
      tables: tableInfo,
      sampleProjects,
      sampleSubprojects
    });
  } catch (error) {
    logger.error('检查数据库失败:', error);
    ctx.fail(5001, `检查数据库失败: ${error.message}`);
  } finally {
    if (db) db.close();
  }
};

module.exports = {
  login,
  getProjects,
  getProjectById,
  migrateProject,
  migrateAll,
  getProgress,
  checkDatabase
};
