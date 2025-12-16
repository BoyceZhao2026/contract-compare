<template>
  <el-dialog
    v-model="dialogVisible"
    :title="currentStep === 'upload' ? '新建合同比对' : '选择比对内容'"
    :width="currentStep === 'upload' ? '600px' : '90%'"
    :before-close="handleClose"
    class="comparison-dialog"
  >
    <!-- 第一步：文件上传 -->
    <div v-if="currentStep === 'upload'" class="upload-step">
      <div class="comparison-form">
        <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
          <el-form-item label="原文件" prop="original">
            <el-upload
              ref="originalUpload"
              class="upload-demo"
              drag
              :auto-upload="false"
              :on-change="(file: any) => handleFileChange(file, 'original')"
              :on-remove="() => handleFileRemove('original')"
              :limit="1"
              accept=".docx,.doc"
            >
              <el-icon class="el-icon--upload"><upload-filled /></el-icon>
              <div class="el-upload__text">
                将文件拖到此处，或<em>点击上传</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">
                  只能上传 .docx 或 .doc 文件，且不超过 50MB
                </div>
              </template>
            </el-upload>
            <div v-if="form.originalFile" class="file-info">
              <el-text type="success" size="small">
                已选择: {{ form.originalFile.name }}
              </el-text>
            </div>
          </el-form-item>

          <el-form-item label="目标文件" prop="target">
            <el-upload
              ref="targetUpload"
              class="upload-demo"
              drag
              :auto-upload="false"
              :on-change="(file: any) => handleFileChange(file, 'target')"
              :on-remove="() => handleFileRemove('target')"
              :limit="1"
              accept=".docx,.doc"
            >
              <el-icon class="el-icon--upload"><upload-filled /></el-icon>
              <div class="el-upload__text">
                将文件拖到此处，或<em>点击上传</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">
                  只能上传 .docx 或 .doc 文件，且不超过 50MB
                </div>
              </template>
            </el-upload>
            <div v-if="form.targetFile" class="file-info">
              <el-text type="success" size="small">
                已选择: {{ form.targetFile.name }}
              </el-text>
            </div>
          </el-form-item>
        </el-form>
      </div>
    </div>

    <!-- 第二步：内容选择 -->
    <div v-if="currentStep === 'select'" class="content-selection-step">
      <div class="selection-container">
        <div class="selection-header">
          <el-alert
            title="选择要对比的内容"
            description="可以选择文档的全部内容或部分内容进行对比。如果不选择，将对比整个文档。"
            type="info"
            :closable="false"
            show-icon
          />
        </div>

        <div class="selection-panels">
          <!-- 左侧文件内容选择 -->
          <div class="selection-panel">
            <div class="panel-header">
              <h3>{{ form.originalFile?.name }} (基准文件)</h3>
              <div class="panel-actions">
                <el-button
                  v-if="originalSelectedContent"
                  type="success"
                  size="small"
                  plain
                  @click="showContentSelector('original')"
                >
                  <el-icon><View /></el-icon>
                  已选择内容 ({{ originalSelectedContent.length }}字符)
                </el-button>
                <el-button
                  v-else
                  type="primary"
                  size="small"
                  plain
                  @click="showContentSelector('original')"
                >
                  <el-icon><Select /></el-icon>
                  选择内容
                </el-button>
                <el-button
                  v-if="originalSelectedContent"
                  type="info"
                  size="small"
                  plain
                  @click="clearContentSelection('original')"
                >
                  <el-icon><CloseBold /></el-icon>
                  清除选择
                </el-button>
              </div>
            </div>
            <div class="panel-content">
              <FileContentSelector
                :file-path="form.originalPath"
                :model-value="originalSelectedContent"
                @update:model-value="(value) => handleContentSelected('original', value)"
                @text-selected="(value) => handleContentSelected('original', value)"
              />
            </div>
          </div>

          <!-- 右侧文件内容选择 -->
          <div class="selection-panel">
            <div class="panel-header">
              <h3>{{ form.targetFile?.name }} (目标文件)</h3>
              <div class="panel-actions">
                <el-button
                  v-if="targetSelectedContent"
                  type="success"
                  size="small"
                  plain
                  @click="showContentSelector('target')"
                >
                  <el-icon><View /></el-icon>
                  已选择内容 ({{ targetSelectedContent.length }}字符)
                </el-button>
                <el-button
                  v-else
                  type="primary"
                  size="small"
                  plain
                  @click="showContentSelector('target')"
                >
                  <el-icon><Select /></el-icon>
                  选择内容
                </el-button>
                <el-button
                  v-if="targetSelectedContent"
                  type="info"
                  size="small"
                  plain
                  @click="clearContentSelection('target')"
                >
                  <el-icon><CloseBold /></el-icon>
                  清除选择
                </el-button>
              </div>
            </div>
            <div class="panel-content">
              <FileContentSelector
                :file-path="form.targetPath"
                :model-value="targetSelectedContent"
                @update:model-value="(value) => handleContentSelected('target', value)"
                @text-selected="(value) => handleContentSelected('target', value)"
              />
            </div>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">
          {{ currentStep === 'upload' ? '取消' : '返回上传' }}
        </el-button>
        <el-button
          v-if="currentStep === 'upload'"
          type="primary"
          @click="handleUpload"
          :loading="uploading"
          :disabled="!form.originalFile || !form.targetFile"
        >
          选择内容
        </el-button>
        <el-button
          v-if="currentStep === 'select'"
          type="primary"
          @click="handleStartComparison"
          :loading="comparing"
        >
          开始比对
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, View, Select, CloseBold } from '@element-plus/icons-vue'
import type { FormInstance, UploadProps, UploadUserFile } from 'element-plus'
import axios from 'axios'
import { useRouter } from 'vue-router'
import FileContentSelector from './FileContentSelector.vue'

interface Props {
  modelValue: boolean
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'created'): void
}

const router = useRouter()
const props = defineProps<Props>()
const emit = defineEmits<Emits>()

// 表单引用
const formRef = ref<FormInstance>()
const originalUpload = ref()
const targetUpload = ref()

// 表单数据
const form = reactive({
  originalFile: null as File | null,
  targetFile: null as File | null,
  originalPath: '',
  targetPath: ''
})

// 步骤状态
const currentStep = ref<'upload' | 'select'>('upload')

// 上传状态
const uploading = ref(false)
const comparing = ref(false)

// 内容选择状态
const originalSelectedContent = ref('')
const targetSelectedContent = ref('')

// 对话框显示状态
const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

// 表单验证规则
const rules = {
  original: [
    {
      required: true,
      message: '请选择原文件',
      trigger: 'change',
      validator: (rule: any, value: any, callback: any) => {
        if (!form.originalFile) {
          callback(new Error('请选择原文件'))
        } else {
          callback()
        }
      }
    }
  ],
  target: [
    {
      required: true,
      message: '请选择目标文件',
      trigger: 'change',
      validator: (rule: any, value: any, callback: any) => {
        if (!form.targetFile) {
          callback(new Error('请选择目标文件'))
        } else {
          callback()
        }
      }
    }
  ]
}

// 文件选择处理
const handleFileChange = (file: UploadUserFile, type: 'original' | 'target') => {
  const rawFile = file.raw as File

  // 验证文件类型
  if (!rawFile.name.toLowerCase().endsWith('.docx') && !rawFile.name.toLowerCase().endsWith('.doc')) {
    ElMessage.error('只能上传 .docx 或 .doc 文件')
    if (type === 'original') {
      originalUpload.value?.clearFiles()
      form.originalFile = null
    } else {
      targetUpload.value?.clearFiles()
      form.targetFile = null
    }
    // 触发验证
    formRef.value?.validateField(type)
    return
  }

  // 验证文件大小
  if (rawFile.size > 50 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 50MB')
    if (type === 'original') {
      originalUpload.value?.clearFiles()
      form.originalFile = null
    } else {
      targetUpload.value?.clearFiles()
      form.targetFile = null
    }
    // 触发验证
    formRef.value?.validateField(type)
    return
  }

  if (type === 'original') {
    form.originalFile = rawFile
    console.log('原文件已选择:', rawFile.name)
  } else {
    form.targetFile = rawFile
    console.log('目标文件已选择:', rawFile.name)
  }

  // 触发验证
  formRef.value?.validateField(type)

  console.log('当前表单状态:', {
    originalFile: form.originalFile?.name,
    targetFile: form.targetFile?.name
  })
}

// 文件移除处理
const handleFileRemove = (type: 'original' | 'target') => {
  if (type === 'original') {
    form.originalFile = null
    form.originalPath = ''
  } else {
    form.targetFile = null
    form.targetPath = ''
  }

  // 触发验证
  formRef.value?.validateField(type)
}

// 上传文件
const uploadFile = async (file: File): Promise<string> => {
  const formData = new FormData()
  formData.append('file', file)

  try {
    const response = await axios.post('/contract/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })

    if (response.data.code === 200) {
      return response.data.data.path
    } else {
      throw new Error(response.data.message || '文件上传失败')
    }
  } catch (error) {
    console.error('文件上传失败:', error)
    throw error
  }
}

// 记录比对日志
const recordComparison = async (originalPath: string, targetPath: string) => {
  try {
    const recordData = {
      batchId: null, // 让后端生成
      originalFilename: form.originalFile?.name,
      targetFilename: form.targetFile?.name,
      originalFilePath: originalPath,
      targetFilePath: targetPath
    }

    const response = await axios.post('/contract/record', recordData)

    if (response.data.code !== 200) {
      throw new Error(response.data.message || '记录保存失败')
    }
  } catch (error) {
    console.error('记录比对失败:', error)
    throw error
  }
}

// 处理上传文件并进入内容选择步骤
const handleUpload = async () => {
  if (!formRef.value) return

  console.log('开始上传文件，表单状态:', {
    originalFile: form.originalFile?.name,
    targetFile: form.targetFile?.name
  })

  try {
    // 表单验证
    await formRef.value.validate()
    console.log('表单验证通过')

    uploading.value = true

    // 检查是否选择了相同的文件
    if (form.originalFile === form.targetFile) {
      ElMessage.error('原文件和目标文件不能相同')
      return
    }

    // 上传原文件
    const originalPath = await uploadFile(form.originalFile!)
    form.originalPath = originalPath

    // 上传目标文件
    const targetPath = await uploadFile(form.targetFile!)
    form.targetPath = targetPath

    console.log('文件上传完成，进入内容选择步骤')

    // 进入内容选择步骤
    currentStep.value = 'select'
    //ElMessage.success('文件上传成功，请选择要比对的内容')

  } catch (error: any) {
    console.error('上传失败:', error)
    ElMessage.error(error.message || '操作失败，请重试')
  } finally {
    uploading.value = false
  }
}

// 处理开始比对
const handleStartComparison = async () => {
  try {
    comparing.value = true

    console.log('开始比对，内容选择状态:', {
      originalSelected: originalSelectedContent.value ? `${originalSelectedContent.value.substring(0, 50)}...` : '全文',
      targetSelected: targetSelectedContent.value ? `${targetSelectedContent.value.substring(0, 50)}...` : '全文'
    })

    // 记录比对日志
    await recordComparison(form.originalPath, form.targetPath)

    // 构建比对查询参数
    const queryParams: any = {
      original: form.originalPath,
      target: form.targetPath
    }

    // 如果有选择的内容，添加到查询参数中
    if (originalSelectedContent.value) {
      queryParams.originalContent = encodeURIComponent(originalSelectedContent.value)
    }
    if (targetSelectedContent.value) {
      queryParams.targetContent = encodeURIComponent(targetSelectedContent.value)
    }

    // 关闭对话框
    handleClose()

    // 触发创建完成事件
    emit('created')

    // 跳转到比对页面
    await router.push({
      name: 'compare',
      query: queryParams
    })

  } catch (error: any) {
    console.error('开始比对失败:', error)
    ElMessage.error(error.message || '操作失败，请重试')
  } finally {
    comparing.value = false
  }
}

// 处理内容选择
const handleContentSelected = (type: 'original' | 'target', content: string) => {
  if (type === 'original') {
    originalSelectedContent.value = content
  } else {
    targetSelectedContent.value = content
  }
}

// 显示内容选择器
const showContentSelector = (type: 'original' | 'target') => {
  // 这里可以扩展为显示单独的选择器对话框
  ElMessage.info('请在下方面板中选择内容')
}

// 清除内容选择
const clearContentSelection = (type: 'original' | 'target') => {
  if (type === 'original') {
    originalSelectedContent.value = ''
  } else {
    targetSelectedContent.value = ''
  }
}

// 关闭对话框
const handleClose = () => {
  if (currentStep.value === 'select' && uploading.value) {
    // 如果在内容选择步骤，点击返回上传
    currentStep.value = 'upload'
    return
  }

  dialogVisible.value = false
  resetForm()
}

// 重置表单
const resetForm = () => {
  formRef.value?.resetFields()
  form.originalFile = null
  form.targetFile = null
  form.originalPath = ''
  form.targetPath = ''
  originalUpload.value?.clearFiles()
  targetUpload.value?.clearFiles()

  // 重置步骤和选择状态
  currentStep.value = 'upload'
  originalSelectedContent.value = ''
  targetSelectedContent.value = ''
  uploading.value = false
  comparing.value = false
}

// 监听对话框关闭，重置表单
watch(dialogVisible, (newVal) => {
  if (!newVal) {
    resetForm()
  }
})
</script>

<style scoped>
.comparison-form {
  padding: 20px 0;
}

.upload-demo {
  width: 100%;
}

.file-info {
  margin-top: 8px;
}

.dialog-footer {
  text-align: right;
}

:deep(.el-upload-dragger) {
  width: 100%;
  height: 120px;
}

:deep(.el-upload__tip) {
  color: #909399;
  font-size: 12px;
}

/* 内容选择步骤样式 */
.comparison-dialog :deep(.el-dialog__body) {
  max-height: 80vh;
  overflow-y: auto;
}

.selection-container {
  .selection-header {
    margin-bottom: 20px;
  }

  .selection-panels {
    display: flex;
    gap: 20px;
    min-height: 400px;
  }

  .selection-panel {
    flex: 1;
    border: 1px solid #e4e7ed;
    border-radius: 8px;
    overflow: hidden;

    .panel-header {
      background: #f5f7fa;
      padding: 16px;
      border-bottom: 1px solid #e4e7ed;

      h3 {
        margin: 0 0 8px 0;
        font-size: 16px;
        color: #2c3e50;
        font-weight: 500;
      }

      .panel-actions {
        display: flex;
        gap: 8px;
        flex-wrap: wrap;
      }
    }

    .panel-content {
      height: 350px;
      overflow: hidden;
    }
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .selection-panels {
    flex-direction: column;
    gap: 15px;
  }

  .selection-panel .panel-content {
    height: 300px;
  }

  .comparison-dialog {
    width: 95% !important;
  }
}
</style>
