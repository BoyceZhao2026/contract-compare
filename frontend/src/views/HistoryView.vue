<template>
  <div class="history-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <div class="header-info">
          <h1 class="page-title">
            <el-icon><Clock /></el-icon>
            合同比对历史
          </h1>
          <p class="page-subtitle">查看和管理所有合同比对记录</p>
        </div>
        <div class="header-actions">
          <el-button type="primary" size="large" @click="showNewComparisonDialog = true" class="new-compare-btn">
            <el-icon><Plus /></el-icon>
            新建比对
          </el-button>
        </div>
      </div>
    </div>

    <!-- 搜索区域 -->
    <div class="search-section">
      <div class="search-container">
        <div class="search-form">
          <div class="search-item">
            <label class="search-label">文件名称搜索：</label>
            <el-input
              v-model="searchForm.filename"
              placeholder="请输入文件名称"
              clearable
              style="width: 200px"
              @keyup.enter="handleSearch"
            />
          </div>
          <div class="search-item">
            <label class="search-label">比对日期范围：</label>
            <el-date-picker
              v-model="searchForm.dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              style="width: 240px"
              @change="handleSearch"
            />
          </div>
          <div class="search-actions">
            <el-button type="primary" @click="handleSearch">
              <el-icon><Search /></el-icon>
              搜索
            </el-button>
            <el-button @click="handleReset">
              <el-icon><RefreshRight /></el-icon>
              重置
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 历史记录列表 -->
    <div class="history-section">
      <el-card class="history-card">
        <div class="card-header">
          <h3>
            <el-icon><List /></el-icon>
            比对记录
          </h3>
          <div class="header-tools">
            <el-button @click="refreshData" :loading="loading">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>

        <div v-loading="loading" class="history-list">
          <div
            v-for="record in historyList"
            :key="record.batch_id"
            class="history-item"
            @click="showDetailDialog(record)"
          >
            <div class="item-header">
              <div class="item-time">
                <el-icon><Clock /></el-icon>
                {{ formatDate(record.create_time) }}
              </div>
            </div>
            <div class="item-content">
              <div class="file-group">
                <div class="file-label">
                  <el-icon><Folder /></el-icon>
                  原文件
                </div>
                <div class="file-list-with-download">
                  <div class="file-tags">
                    <el-tag
                      v-for="filename in (record.original_filenames ? record.original_filenames.split(',') : [])"
                      :key="filename"
                      type="info"
                      size="small"
                      class="file-tag"
                    >
                      {{ filename }}
                    </el-tag>
                  </div>
                  <el-button
                    v-if="record.original_filenames"
                    type="text"
                    size="small"
                    @click.stop="downloadFile(record, 'original')"
                    class="download-btn"
                    title="下载原文件"
                  >
                    <el-icon><Download /></el-icon>
                  </el-button>
                </div>
              </div>
              <div class="vs-divider">VS</div>
              <div class="file-group">
                <div class="file-label">
                  <el-icon><FolderOpened /></el-icon>
                  目标文件
                </div>
                <div class="file-list-with-download">
                  <div class="file-tags">
                    <el-tag
                      v-for="filename in (record.target_filenames ? record.target_filenames.split(',') : [])"
                      :key="filename"
                      type="success"
                      size="small"
                      class="file-tag"
                    >
                      {{ filename }}
                    </el-tag>
                  </div>
                  <el-button
                    v-if="record.target_filenames"
                    type="text"
                    size="small"
                    @click.stop="downloadFile(record, 'target')"
                    class="download-btn"
                    title="下载目标文件"
                  >
                    <el-icon><Download /></el-icon>
                  </el-button>
                </div>
              </div>
            </div>
            <div class="item-actions">
              <el-button type="text" @click.stop="showDetailDialog(record)">
                <el-icon><View /></el-icon>
                查看详情
              </el-button>

            </div>
          </div>


        </div>

        <!-- 分页 -->
        <div v-if="historyList.length > 0" class="pagination-wrapper">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
          />
        </div>
      </el-card>
    </div>

    <!-- 新建比对对话框 -->
    <NewComparisonDialog
      v-model="showNewComparisonDialog"
      @created="onComparisonCreated"
    />

    <!-- 详情对话框 -->
    <el-dialog
      v-model="showDetailDialogVisible"
      title="比对详情"
      width="95%"
      :before-close="handleDetailClose"
      class="detail-dialog"
      top="2vh"
    >
      <div v-if="selectedRecord" class="detail-header">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="批次ID">
            <el-tag type="primary">{{ selectedRecord.batch_id }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="比对时间">
            {{ formatDate(selectedRecord.create_time) }}
          </el-descriptions-item>
          <el-descriptions-item label="文件数量">
            {{ detailRecords.length }}
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 比对结果展示 -->
      <div v-if="showDiffResult" class="diff-result">
        <div class="diff-controls">
          <el-button @click="handleDetailClose">
            <el-icon><Close /></el-icon>
            关闭
          </el-button>
        </div>
        <DiffViewer
          :key="`diff-${currentOriginalPath}-${currentTargetPath}`"
          ref="diffViewerRef"
          :left-path="currentOriginalPath"
          :right-path="currentTargetPath"
        />
      </div>

      <!-- 加载状态 -->
      <div v-else-if="detailLoading" class="loading-container">
        <el-skeleton :rows="10" animated />
        <div class="loading-text">正在加载比对结果...</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Plus, Clock, Document, Calendar, Files, List, Refresh, Search, RefreshRight,
  Folder, FolderOpened, View, Close, Download
} from '@element-plus/icons-vue'
import axios from 'axios'
import NewComparisonDialog from '../components/NewComparisonDialog.vue'
import DiffViewer from '../components/DiffViewer.vue'
import { useRouter } from 'vue-router'

const router = useRouter()

interface HistoryRecord {
  batch_id: string
  create_time: string
  original_filenames: string
  target_filenames: string
}

interface DetailRecord {
  id: number
  batchId: string
  originalFilename: string
  targetFilename: string
  originalFilePath: string
  targetFilePath: string
  createTime: string
}

// 响应式数据
const loading = ref(false)
const detailLoading = ref(false)
const historyList = ref<HistoryRecord[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const showNewComparisonDialog = ref(false)
const showDetailDialogVisible = ref(false)
const selectedRecord = ref<HistoryRecord | null>(null)
const detailRecords = ref<DetailRecord[]>([])
const diffViewerRef = ref<InstanceType<typeof DiffViewer> | null>(null)
const showDiffResult = ref(false)
const currentOriginalPath = ref('')
const currentTargetPath = ref('')

// 搜索表单数据
const searchForm = ref({
  filename: '',
  dateRange: [] as string[]
})

// 刷新数据
const refreshData = () => {
  fetchHistory()
}

// 从列表重新比对
const replayComparisonFromList = async (record: HistoryRecord) => {
  try {
    const details = await axios.get(`/api/contract/history/${record.batch_id}`)
    if (details.data.code === 200 && details.data.data.length > 0) {
      const firstRecord = details.data.data[0]
      router.push({
        name: 'compare',
        query: {
          original: firstRecord.originalFilePath,
          target: firstRecord.targetFilePath
        }
      })
    }
  } catch (error) {
    ElMessage.error('获取文件信息失败')
  }
}

// 下载文件
const downloadFile = async (record: HistoryRecord, type: 'original' | 'target') => {
  try {
    // 获取该记录的详细信息以获取文件路径
    const details = await axios.get(`/api/contract/history/${record.batch_id}`)

    if (details.data.code !== 200 || details.data.data.length === 0) {
      ElMessage.error('获取文件信息失败')
      return
    }

    // 找到对应的文件记录
    const fileRecord = details.data.data[0]
    let filePath: string
    let fileName: string

    if (type === 'original') {
      filePath = fileRecord.originalFilePath
      fileName = fileRecord.originalFilename
    } else {
      filePath = fileRecord.targetFilePath
      fileName = fileRecord.targetFilename
    }

    if (!filePath) {
      ElMessage.error(`${type === 'original' ? '原' : '目标'}文件路径不存在`)
      return
    }

    // 下载文件
    const response = await axios.get('/api/contract/file/stream', {
      params: { path: filePath },
      responseType: 'blob'
    })

    // 创建下载链接
    const blob = new Blob([response.data])
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName || 'document.docx'
    document.body.appendChild(link)
    link.click()

    // 清理
    window.URL.revokeObjectURL(url)
    document.body.removeChild(link)

    ElMessage.success('文件下载成功')
  } catch (error) {
    console.error('文件下载失败:', error)
    ElMessage.error('文件下载失败')
  }
}

// 搜索处理
const handleSearch = () => {
  currentPage.value = 1
  fetchHistory()
}

// 重置搜索
const handleReset = () => {
  searchForm.value = {
    filename: '',
    dateRange: []
  }
  currentPage.value = 1
  fetchHistory()
}

// 获取历史记录
const fetchHistory = async () => {
  loading.value = true
  try {
    const params: any = {
      page: currentPage.value,
      size: pageSize.value
    }

    // 添加搜索参数
    if (searchForm.value.filename) {
      params.filename = searchForm.value.filename
    }
    if (searchForm.value.dateRange && searchForm.value.dateRange.length === 2) {
      params.startDate = searchForm.value.dateRange[0]
      params.endDate = searchForm.value.dateRange[1]
    }

    const response = await axios.get('/api/contract/history', { params })

    if (response.data.code === 200) {
      historyList.value = response.data.data.records
      total.value = response.data.data.total
    } else {
      ElMessage.error(response.data.message || '获取历史记录失败')
    }
  } catch (error) {
    console.error('获取历史记录失败:', error)
    ElMessage.error('获取历史记录失败')
  } finally {
    loading.value = false
  }
}

// 获取比对详情
const fetchDetail = async (batchId: string) => {
  detailLoading.value = true
  try {
    const response = await axios.get(`/api/contract/history/${batchId}`)

    if (response.data.code === 200) {
      detailRecords.value = response.data.data

      console.log('获取到的比对详情数据:', detailRecords.value)

      // 如果有记录，直接开始比对
      if (detailRecords.value.length > 0) {
        const firstRecord = detailRecords.value[0]
        console.log('第一条记录详情:', firstRecord)
        console.log('文件路径检查:', {
          originalFilePath: firstRecord?.originalFilePath,
          targetFilePath: firstRecord?.targetFilePath,
          originalFilename: firstRecord?.originalFilename,
          targetFilename: firstRecord?.targetFilename
        })

        // 使用驼峰格式的字段名（根据实际返回的数据结构）
        const originalPath = firstRecord?.originalFilePath
        const targetPath = firstRecord?.targetFilePath

        if (firstRecord && originalPath && targetPath) {
          currentOriginalPath.value = originalPath
          currentTargetPath.value = targetPath

          console.log('设置文件路径成功:', {
            original: currentOriginalPath.value,
            target: currentTargetPath.value
          })

          // 等待DOM更新后启动比对
          await nextTick()
          showDiffResult.value = true

          // 等待DiffViewer组件渲染完成
          let attempts = 0
          const waitForDiffViewer = () => {
            attempts++
            if (diffViewerRef.value) {
              console.log('DiffViewer组件已加载，开始比对')
              startHistoryDiff()
            } else if (attempts < 20) {
              console.log(`等待DiffViewer组件加载... (${attempts}/20)`)
              setTimeout(waitForDiffViewer, 100)
            } else {
              console.error('DiffViewer组件加载超时')
              ElMessage.error('比对组件加载失败，请重试')
            }
          }

          setTimeout(waitForDiffViewer, 100)
        } else {
          console.error('文件路径信息不完整:', firstRecord)
          ElMessage.error(`文件路径信息不完整，请检查比对记录。原始文件路径: ${originalPath || '空'}, 目标文件路径: ${targetPath || '空'}`)
        }
      } else {
        ElMessage.error('没有找到比对记录')
      }
    } else {
      ElMessage.error(response.data.message || '获取详情失败')
    }
  } catch (error) {
    console.error('获取详情失败:', error)
    ElMessage.error('获取详情失败')
  } finally {
    detailLoading.value = false
  }
}

// 显示详情对话框
const showDetailDialog = async (record: HistoryRecord) => {
  selectedRecord.value = record
  showDetailDialogVisible.value = true
  await fetchDetail(record.batch_id)
}

// 格式化日期
const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleString('zh-CN')
}

// 分页处理
const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchHistory()
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  fetchHistory()
}

// 新建比对完成
const onComparisonCreated = () => {
  showNewComparisonDialog.value = false
  fetchHistory()
}

// 重新比对
const replayComparison = () => {
  if (detailRecords.value.length > 0) {
    const firstRecord = detailRecords.value[0]
    // 跳转到比对页面并传递文件路径
    router.push({
      name: 'compare',
      query: {
        original: firstRecord.originalFilePath,
        target: firstRecord.targetFilePath
      }
    })
    showDetailDialogVisible.value = false
  }
}

// 开始历史比对
const startHistoryDiff = async () => {
  if (!diffViewerRef.value) {
    console.error('DiffViewer 组件未加载')
    ElMessage.error('比对组件未加载，请重试')
    return
  }

  if (!currentOriginalPath.value || !currentTargetPath.value) {
    console.error('文件路径为空:', {
      original: currentOriginalPath.value,
      target: currentTargetPath.value
    })
    ElMessage.error('文件路径为空，请重试')
    return
  }

  try {
    console.log('开始历史比对:', {
      original: currentOriginalPath.value,
      target: currentTargetPath.value
    })

    // 检查DiffViewer是否有startDiff方法
    if (typeof diffViewerRef.value.startDiff !== 'function') {
      console.error('DiffViewer没有startDiff方法')
      ElMessage.error('比对组件方法缺失，请重试')
      return
    }

    await diffViewerRef.value.startDiff()
    console.log('历史比对完成')
  } catch (error) {
    console.error('历史比对失败:', error)
    ElMessage.error('比对失败: ' + (error as Error).message)
  }
}

// 关闭详情对话框
const handleDetailClose = () => {
  showDetailDialogVisible.value = false
  showDiffResult.value = false
  selectedRecord.value = null
  detailRecords.value = []
  currentOriginalPath.value = ''
  currentTargetPath.value = ''
}

// 组件挂载时获取数据
onMounted(() => {
  fetchHistory()
})
</script>

<style scoped>
.history-page {
  min-height: calc(100vh - 64px);
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
}

/* 页面头部 */
.page-header {
  background: white;
  padding: 32px 0;
  border-bottom: 1px solid #e4e7ed;
  margin-bottom: 32px;
}

.header-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title {
  margin: 0 0 8px 0;
  color: #2c3e50;
  font-size: 32px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title .el-icon {
  color: #667eea;
  font-size: 36px;
}

.page-subtitle {
  margin: 0;
  color: #7f8c8d;
  font-size: 16px;
}

.new-compare-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  padding: 12px 32px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
  transition: all 0.3s ease;
}

.new-compare-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(102, 126, 234, 0.4);
}

/* 搜索区域 */
.search-section {
  max-width: 1400px;
  margin: 0 auto 32px;
  padding: 0 20px;
}

.search-container {
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  padding: 24px;
}

.search-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 20px;
}

.search-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.search-label {
  color: #5a6c7d;
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
}

.search-actions {
  display: flex;
  gap: 12px;
  margin-left: auto;
}

/* 历史记录部分 */
.history-section {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 20px;
}

.history-card {
  border: none;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  border-radius: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px;
  border-bottom: 1px solid #f0f2f5;
}

.card-header h3 {
  margin: 0;
  color: #2c3e50;
  font-size: 20px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-header .el-icon {
  color: #667eea;
}

.history-list {
  padding: 24px;
  min-height: 400px;
}

.history-item {
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 16px;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
}

.history-item:hover {
  background: white;
  border-color: #667eea;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.15);
  transform: translateY(-2px);
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  color: #7f8c8d;
  font-size: 14px;
}

.item-time,
.item-ip {
  display: flex;
  align-items: center;
  gap: 6px;
}

.item-content {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 16px;
}

.file-group {
  flex: 1;
}

.file-label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #5a6c7d;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 8px;
}

.file-list-with-download {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex: 1;
}

.file-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex: 1;
}

.download-btn {
  padding: 2px 4px;
  min-width: 28px;
  height: 24px;
  border-radius: 4px;
}

.download-btn:hover {
  background-color: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.file-tag {
  border-radius: 6px;
  font-weight: 500;
}

.vs-divider {
  font-weight: 700;
  color: #667eea;
  font-size: 18px;
  padding: 0 16px;
  position: relative;
  display: flex;
  align-items: center;
}

.item-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.item-actions .el-button {
  color: #667eea;
  font-weight: 500;
}

.item-actions .el-button:hover {
  color: #4a5fc1;
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding: 24px;
  border-top: 1px solid #f0f2f5;
}

/* 详情对话框 */
.detail-dialog :deep(.el-dialog) {
  border-radius: 12px;
  overflow: hidden;
}

.detail-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.detail-header {
  padding: 20px 24px;
  border-bottom: 1px solid #f0f2f5;
  background: #f8f9fa;
}

.diff-result {
  height: 70vh;
  overflow: hidden;
}

.diff-controls {
  padding: 12px 24px;
  border-bottom: 1px solid #f0f2f5;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.loading-container {
  padding: 40px;
  text-align: center;
}

.loading-text {
  margin-top: 20px;
  color: #7f8c8d;
  font-size: 16px;
}

/* DiffViewer样式适配 */
.diff-result :deep(.diff-viewer-container) {
  margin: 0;
  padding: 0;
  width: 100%;
  height: calc(100% - 60px);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .header-content {
    flex-direction: column;
    align-items: stretch;
    gap: 20px;
    text-align: center;
  }

  .page-title {
    font-size: 24px;
    justify-content: center;
  }

  .search-section {
    padding: 0 16px;
  }

  .search-form {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
  }

  .search-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .search-actions {
    margin-left: 0;
    justify-content: center;
  }

  .history-section {
    padding: 0 16px;
  }

  .item-content {
    flex-direction: column;
    align-items: stretch;
  }

  .vs-divider {
    text-align: center;
    padding: 8px 0;
  }

  .file-comparison {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .file-vs {
    transform: rotate(90deg);
  }

  .item-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .item-actions {
    justify-content: center;
  }
}

@media (max-width: 480px) {
  .page-header {
    padding: 20px 0;
  }

  .history-item {
    padding: 16px;
  }

  .card-header {
    padding: 16px;
  }

  .history-list {
    padding: 16px;
  }
}
</style>
