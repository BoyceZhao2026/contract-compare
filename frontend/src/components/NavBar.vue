<template>
  <div class="navbar">
    <div class="navbar-content">
      <div class="navbar-brand">
        <el-icon class="brand-icon"><Document /></el-icon>
        <span class="brand-text">合同模板对比（内测体验版）</span>
      </div>

    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Document, Clock, InfoFilled, Edit } from '@element-plus/icons-vue'

const route = useRoute()

const activeIndex = computed(() => route.path)
</script>

<style scoped>
.navbar {
  background: linear-gradient(135deg, #417FF2 0%, #2D5FD8 100%);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  position: sticky;
  top: 0;
  z-index: 1000;
}

.navbar-content {
  padding: 0 20px;
  height: 64px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.navbar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-icon {
  font-size: 28px;
  color: white;
}

.brand-text {
  color: white;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.navbar-menu {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.8);
  text-decoration: none;
  transition: all 0.3s ease;
  font-weight: 500;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.15);
  color: white;
  transform: translateY(-1px);
}

.nav-item.active {
  background: rgba(255, 255, 255, 0.2);
  color: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.nav-item .el-icon {
  font-size: 18px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .navbar-content {
    padding: 0 16px;
  }

  .brand-text {
    font-size: 16px;
  }

  .navbar-menu {
    gap: 4px;
  }

  .nav-item {
    padding: 6px 12px;
    font-size: 14px;
  }

  .nav-item span {
    display: none;
  }

  .nav-item .el-icon {
    font-size: 20px;
  }
}
</style>
