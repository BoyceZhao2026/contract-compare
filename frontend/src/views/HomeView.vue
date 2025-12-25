<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ContractCompare from '../components/ContractCompare.vue';
import HistoryTab from '../components/HistoryTab.vue';

const route = useRoute();
const router = useRouter();
const activeTab = ref('compare');

// 监听路由变化，切换tab
watch(() => route.query.tab, (newTab) => {
  if (newTab) {
    activeTab.value = newTab as string;
  }
}, { immediate: true });

// 监听tab切换，更新路由
watch(activeTab, (newTab) => {
  const currentTab = route.query.tab;
  if (currentTab !== newTab) {
    router.replace({
      query: { tab: newTab }
    });
  }
});
</script>

<template>
  <div class="home-view">
    <el-tabs v-model="activeTab" class="home-tabs">
      <el-tab-pane label="合同比对" name="compare">
        <ContractCompare />
      </el-tab-pane>
      <el-tab-pane label="比对历史" name="history">
        <HistoryTab />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.home-view {
  padding: 0;
  overflow: hidden;
}

.home-tabs {
  width: 100%;
}

:deep(.el-tabs__content) {
  width: 100%;
  overflow: hidden;
}

:deep(.el-tab-pane) {
  width: 100%;
}

:deep(.el-tabs__header) {
  margin: 0 0 20px 0;
  padding: 0;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

:deep(.el-tabs__nav-wrap::after) {
  display: none;
}

:deep(.el-tabs__nav-wrap) {
  padding: 0 20px;
}

:deep(.el-tabs__item) {
  font-size: 16px;
  font-weight: 500;
  color: #5a6c7d;
  padding: 0 30px;
  height: 50px;
  line-height: 50px;
}

:deep(.el-tabs__item.is-active) {
  color: #417FF2;
}

:deep(.el-tabs__active-bar) {
  background-color: #417FF2;
  height: 3px;
  border-radius: 2px;
}

:deep(.el-tabs__content) {
  padding: 0;
}
</style>
