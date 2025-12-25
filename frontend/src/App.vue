<script setup lang="ts">
import { RouterView } from 'vue-router'
import NavBar from './components/NavBar.vue'
</script>

<template>
  <div id="app">
    <div class="app-container">
      <NavBar />
      <RouterView />
    </div>
  </div>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB',
    'Microsoft YaHei', '微软雅黑', Arial, sans-serif;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  color: #2c3e50;
  line-height: 1.6;
  min-height: 100vh;
  overflow-x: hidden;
  width: 100%;
}

#app {
  min-height: 100vh;
  padding: 0;
  display: flex;
  flex-direction: column;
  overflow-x: hidden;
  width: 100%;
  max-width: 100vw;
}

.app-container {
  max-width: 1460px;
  margin: 0 auto;
  width: 100%;
}

/* 全局Element Plus样式优化 */
:deep(.el-button) {
  border-radius: 8px;
  font-weight: 500;
  transition: all 0.3s ease;
}

:deep(.el-button--primary) {
  background: linear-gradient(135deg, #417FF2 0%, #2D5FD8 100%);
  border: none;
  box-shadow: 0 2px 8px rgba(65, 127, 242, 0.3);
}

:deep(.el-button--primary:hover) {
  background: linear-gradient(135deg, #2D5FD8 0%, #1A4BC2 100%);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(65, 127, 242, 0.4);
}

:deep(.el-card) {
  border: none;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  border-radius: 12px;
  transition: all 0.3s ease;
}

:deep(.el-card:hover) {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

:deep(.el-card__header) {
  border-bottom: 1px solid #f0f2f5;
  padding: 20px 24px;
}

:deep(.el-card__body) {
  padding: 24px;
}

:deep(.el-dialog) {
  border-radius: 12px;
  overflow: hidden;
}

:deep(.el-dialog__header) {
  background: linear-gradient(135deg, #417FF2 0%, #2D5FD8 100%);
  color: white;
  padding: 20px 24px;
}

:deep(.el-dialog__title) {
  color: white;
  font-weight: 600;
  font-size: 18px;
}

:deep(.el-dialog__headerbtn .el-dialog__close) {
  color: white;
  font-size: 20px;
}

:deep(.el-dialog__body) {
  padding: 24px;
}

:deep(.el-table) {
  border-radius: 8px;
  overflow: hidden;
}

:deep(.el-table th) {
  background: #f8f9fa;
  color: #5a6c7d;
  font-weight: 600;
  border-bottom: 2px solid #e9ecef;
}

:deep(.el-table td) {
  border-bottom: 1px solid #f0f2f5;
}

:deep(.el-table--enable-row-hover .el-table__body tr:hover > td) {
  background: #f8f9fa;
}

:deep(.el-pagination) {
  justify-content: center;
}

:deep(.el-pagination .el-pager li) {
  border-radius: 6px;
  margin: 0 2px;
}

:deep(.el-pagination .el-pager li.active) {
  background: linear-gradient(135deg, #417FF2 0%, #2D5FD8 100%);
}

:deep(.el-tag) {
  border-radius: 6px;
  border: none;
  font-weight: 500;
}

:deep(.el-tag--info) {
  background: #e9ecef;
  color: #495057;
}

:deep(.el-tag--success) {
  background: #d4edda;
  color: #155724;
}

:deep(.el-empty) {
  padding: 60px 20px;
}

:deep(.el-empty__description) {
  color: #7f8c8d;
  font-size: 16px;
}

:deep(.el-loading-mask) {
  border-radius: 12px;
}

:deep(.el-descriptions) {
  border-radius: 8px;
  overflow: hidden;
}

:deep(.el-descriptions__header) {
  background: #f8f9fa;
  margin-bottom: 0;
}

:deep(.el-descriptions__body) {
  background: white;
}

:deep(.el-descriptions-item__label) {
  font-weight: 600;
  color: #5a6c7d;
}

/* 滚动条样式 */
::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 4px;
}

::-webkit-scrollbar-thumb {
  background: linear-gradient(135deg, #417FF2 0%, #2D5FD8 100%);
  border-radius: 4px;
}

::-webkit-scrollbar-thumb:hover {
  background: linear-gradient(135deg, #2D5FD8 0%, #1A4BC2 100%);
}

/* 动画效果 */
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.fade-in-up {
  animation: fadeInUp 0.6s ease-out;
}

@keyframes slideInRight {
  from {
    opacity: 0;
    transform: translateX(30px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.slide-in-right {
  animation: slideInRight 0.4s ease-out;
}

/* 响应式字体大小 */
@media (max-width: 768px) {
  body {
    font-size: 14px;
  }
}

@media (max-width: 480px) {
  body {
    font-size: 13px;
  }
}
</style>
