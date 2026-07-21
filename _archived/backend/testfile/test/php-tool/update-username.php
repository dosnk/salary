<?php
// 修改用户名工具
$host = 'localhost';
$port = 5432;
$dbname = 'salary';
$user = 'salary';
$password = 'sj27fsAy7rSDDss6';

try {
    $dsn = "pgsql:host=$host;port=$port;dbname=$dbname";
    $pdo = new PDO($dsn, $user, $password);
    $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);

    if ($_SERVER['REQUEST_METHOD'] === 'POST') {
        $action = $_POST['action'] ?? '';

        if ($action === 'view') {
            $stmt = $pdo->query("SELECT id, username, nickname, phone, role FROM users ORDER BY id");
            $users = $stmt->fetchAll(PDO::FETCH_ASSOC);
            echo json_encode(['success' => true, 'users' => $users]);
            exit;
        }

        if ($action === 'update') {
            $userId = $_POST['user_id'] ?? 0;
            $newUsername = trim($_POST['new_username'] ?? '');

            if (!$userId || !$newUsername) {
                echo json_encode(['success' => false, 'message' => '参数不完整']);
                exit;
            }

            $checkStmt = $pdo->prepare("SELECT COUNT(*) FROM users WHERE id = ?");
            $checkStmt->execute([$userId]);
            if ($checkStmt->fetchColumn() == 0) {
                echo json_encode(['success' => false, 'message' => '用户不存在']);
                exit;
            }

            $updateStmt = $pdo->prepare("UPDATE users SET username = ? WHERE id = ?");
            $updateStmt->execute([$newUsername, $userId]);

            echo json_encode(['success' => true, 'message' => '修改成功']);
            exit;
        }
    }
} catch (Exception $e) {
    echo json_encode(['success' => false, 'message' => $e->getMessage()]);
    exit;
}
?>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>修改用户名工具</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: 'Microsoft YaHei', sans-serif; padding: 20px; background: #f5f5f5; }
        .container { max-width: 900px; margin: 0 auto; background: #fff; padding: 30px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        h1 { color: #333; margin-bottom: 20px; }
        .btn { padding: 8px 20px; border: none; border-radius: 4px; cursor: pointer; font-size: 14px; }
        .btn-primary { background: #0ea5e9; color: #fff; }
        .btn-primary:hover { background: #0284c7; }
        .btn-danger { background: #ef4444; color: #fff; }
        .btn-success { background: #10b981; color: #fff; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        th, td { border: 1px solid #ddd; padding: 12px; text-align: left; }
        th { background: #f9fafb; font-weight: 600; }
        tr:hover { background: #f9fafb; }
        .form-group { margin-top: 20px; padding: 20px; background: #f9fafb; border-radius: 4px; }
        .form-row { display: flex; gap: 15px; align-items: center; margin-bottom: 15px; }
        label { width: 100px; font-weight: 600; }
        input { padding: 8px 12px; border: 1px solid #ddd; border-radius: 4px; flex: 1; font-size: 14px; }
        .message { margin-top: 15px; padding: 12px; border-radius: 4px; display: none; }
        .message.success { background: #d1fae5; color: #065f46; border: 1px solid #6ee7b7; }
        .message.error { background: #fee2e2; color: #991b1b; border: 1px solid #fca5a5; }
        .role-tag { padding: 2px 8px; border-radius: 4px; font-size: 12px; }
        .role-admin { background: #e6f7ff; color: #1890ff; }
        .role-documenter { background: #f6ffed; color: #52c41a; }
        .role-constructor { background: #fff7e6; color: #fa8c16; }
    </style>
</head>
<body>
    <div class="container">
        <h1>修改用户名工具</h1>
        
        <button class="btn btn-primary" onclick="loadUsers()">刷新用户列表</button>
        
        <table id="userTable">
            <thead>
                <tr>
                    <th>ID</th>
                    <th>用户名</th>
                    <th>昵称</th>
                    <th>手机号</th>
                    <th>角色</th>
                    <th>操作</th>
                </tr>
            </thead>
            <tbody id="userTableBody">
                <tr><td colspan="6" style="text-align:center;">点击刷新按钮加载用户列表</td></tr>
            </tbody>
        </table>

        <div class="form-group" id="editForm" style="display:none;">
            <h3>修改用户名</h3>
            <div class="form-row">
                <label>用户ID：</label>
                <input type="text" id="editUserId" readonly>
            </div>
            <div class="form-row">
                <label>当前用户名：</label>
                <input type="text" id="editOldUsername" readonly>
            </div>
            <div class="form-row">
                <label>新用户名：</label>
                <input type="text" id="editNewUsername" placeholder="请输入新用户名">
            </div>
            <div style="display:flex;gap:10px;">
                <button class="btn btn-success" onclick="submitUpdate()">确认修改</button>
                <button class="btn btn-danger" onclick="cancelEdit()">取消</button>
            </div>
            <div id="message" class="message"></div>
        </div>
    </div>

    <script>
        let users = [];

        function loadUsers() {
            const formData = new FormData();
            formData.append('action', 'view');

            fetch('', {
                method: 'POST',
                body: formData
            })
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    users = data.users;
                    renderTable();
                } else {
                    showMessage(data.message || '加载失败', 'error');
                }
            })
            .catch(err => {
                showMessage('请求失败: ' + err.message, 'error');
            });
        }

        function renderTable() {
            const tbody = document.getElementById('userTableBody');
            if (users.length === 0) {
                tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;">暂无用户</td></tr>';
                return;
            }

            const roleMap = {
                admin: { text: '管理员', class: 'role-admin' },
                documenter: { text: '资料员', class: 'role-documenter' },
                constructor: { text: '施工员', class: 'role-constructor' }
            };

            tbody.innerHTML = users.map(user => {
                const role = roleMap[user.role] || { text: user.role, class: '' };
                return `
                    <tr>
                        <td>${user.id}</td>
                        <td>${user.username}</td>
                        <td>${user.nickname || '-'}</td>
                        <td>${user.phone || '-'}</td>
                        <td><span class="role-tag ${role.class}">${role.text}</span></td>
                        <td><button class="btn btn-primary" onclick="startEdit(${user.id}, '${user.username}')">修改</button></td>
                    </tr>
                `;
            }).join('');
        }

        function startEdit(userId, oldUsername) {
            document.getElementById('editUserId').value = userId;
            document.getElementById('editOldUsername').value = oldUsername;
            document.getElementById('editNewUsername').value = '';
            document.getElementById('editForm').style.display = 'block';
            document.getElementById('message').style.display = 'none';
        }

        function cancelEdit() {
            document.getElementById('editForm').style.display = 'none';
        }

        function submitUpdate() {
            const userId = document.getElementById('editUserId').value;
            const newUsername = document.getElementById('editNewUsername').value.trim();

            if (!newUsername) {
                showMessage('请输入新用户名', 'error');
                return;
            }

            const formData = new FormData();
            formData.append('action', 'update');
            formData.append('user_id', userId);
            formData.append('new_username', newUsername);

            fetch('', {
                method: 'POST',
                body: formData
            })
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    showMessage(data.message || '修改成功', 'success');
                    loadUsers();
                    cancelEdit();
                } else {
                    showMessage(data.message || '修改失败', 'error');
                }
            })
            .catch(err => {
                showMessage('请求失败: ' + err.message, 'error');
            });
        }

        function showMessage(msg, type) {
            const el = document.getElementById('message');
            el.textContent = msg;
            el.className = 'message ' + type;
            el.style.display = 'block';
        }
    </script>
</body>
</html>
