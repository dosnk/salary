/**
 * Worker线程池
 *
 * 管理排料计算Worker的创建和复用
 * 避免频繁创建/销毁Worker的开销
 */

const { Worker } = require('worker_threads');
const path = require('path');
const logger = require('../../config/logger');

const WORKER_FILE = path.join(__dirname, 'layoutWorker.js');
const MAX_WORKERS = 4; // 最大Worker数

class WorkerPool {
  constructor() {
    this.workers = [];
    this.queue = [];
  }

  /**
   * 提交排料计算任务
   * @param {object} params - { roomLength, roomWidth, materials }
   * @returns {Promise<object>} 计算结果
   */
  calculate(params) {
    return new Promise((resolve, reject) => {
      const task = { params, resolve, reject };
      this.queue.push(task);
      this.processQueue();
    });
  }

  /**
   * 处理队列中的任务
   */
  processQueue() {
    while (this.queue.length > 0 && this.workers.length < MAX_WORKERS) {
      const task = this.queue.shift();
      this.runWorker(task);
    }
  }

  /**
   * 运行Worker执行任务
   */
  runWorker(task) {
    const worker = new Worker(WORKER_FILE, {
      workerData: task.params,
    });

    this.workers.push(worker);

    worker.on('message', (result) => {
      this.removeWorker(worker);
      if (result.success) {
        task.resolve(result.data);
      } else {
        task.reject(new Error(result.error));
      }
      this.processQueue();
    });

    worker.on('error', (error) => {
      this.removeWorker(worker);
      logger.error('Worker执行错误:', error);
      task.reject(error);
      this.processQueue();
    });

    worker.on('exit', (code) => {
      this.removeWorker(worker);
      if (code !== 0) {
        logger.warn(`Worker异常退出，code: ${code}`);
      }
    });
  }

  /**
   * 从活跃列表中移除Worker
   */
  removeWorker(worker) {
    const index = this.workers.indexOf(worker);
    if (index > -1) {
      this.workers.splice(index, 1);
    }
  }
}

// 单例
const workerPool = new WorkerPool();

module.exports = { workerPool };
