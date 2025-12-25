<script setup lang="ts">
import { ref, onMounted, nextTick, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { UploadProps, UploadFile } from 'element-plus';
import { UploadFilled, DocumentCopy, Select, ArrowLeft } from '@element-plus/icons-vue';
import DiffViewer from './DiffViewer.vue';
import FileContentSelector from './FileContentSelector.vue';
import axios from 'axios';
import { useRoute, useRouter } from 'vue-router';

interface FileInfo {
  name: string;
  path: string;
  size: number;
}

const leftFile = ref<FileInfo | null>(null);
const rightFile = ref<FileInfo | null>(null);
const isComparing = ref(false);
const showDiffViewer = ref(false);

// 内容选择相关
const leftSelectedContent = ref('');
const rightSelectedContent = ref('');
const showContentSelector = ref(false);

const diffViewerRef = ref<InstanceType<typeof DiffViewer> | null>(null);
const route = useRoute();
const router = useRouter();

// 文件上传前的验证
const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  const isDocx = file.type === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' ||
                 file.name.toLowerCase().endsWith('.docx') ||
                 file.type === 'application/msword' ||
                 file.name.toLowerCase().endsWith('.doc');
  const isLt50M = file.size / 1024 / 1024 < 50;

  if (!isDocx) {
    ElMessage.error('只能上传 .docx 或 .doc 格式的文件!');
    return false;
  }
  if (!isLt50M) {
    ElMessage.error('上传文件大小不能超过 50MB!');
    return false;
  }
  return true;
};

// 处理文件上传
const handleFileUpload = async (file: UploadFile, side: 'left' | 'right') => {
  try {
    const formData = new FormData();
    formData.append('file', file.raw!);

    const response = await axios.post('/contract/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });

    if (response.data.code === 200) {
      const fileInfo: FileInfo = {
        name: response.data.data.name,
        path: response.data.data.path,
        size: parseInt(response.data.data.size),
      };

      if (side === 'left') {
        leftFile.value = fileInfo;
      } else {
        rightFile.value = fileInfo;
      }

      ElMessage.success('文件上传成功!');
    } else {
      ElMessage.error(response.data.message || '上传失败');
    }
  } catch (error) {
    console.error('上传失败:', error);
    ElMessage.error('上传失败，请重试');
  }
};

// 左侧文件上传
const handleLeftUpload: UploadProps['onChange'] = (uploadFile) => {
  if (uploadFile.raw) {
    handleFileUpload(uploadFile, 'left');
  }
};

// 右侧文件上传
const handleRightUpload: UploadProps['onChange'] = (uploadFile) => {
  if (uploadFile.raw) {
    handleFileUpload(uploadFile, 'right');
  }
};

// 开始比对
const startComparison = async () => {
  if (!leftFile.value || !rightFile.value) {
    ElMessage.warning('请上传两个文件后再进行比对');
    return;
  }

  console.log('开始比对，文件信息:', {
    leftFile: leftFile.value,
    rightFile: rightFile.value,
    leftSelectedContent: leftSelectedContent.value ? `${leftSelectedContent.value.substring(0, 50)}...` : '全文',
    rightSelectedContent: rightSelectedContent.value ? `${rightSelectedContent.value.substring(0, 50)}...` : '全文'
  });

  // 关闭内容选择器
  showContentSelector.value = false;

  isComparing.value = true;
  showDiffViewer.value = true;

  // 等待DOM更新后调用比对方法
  await new Promise(resolve => setTimeout(resolve, 100));

  // 多次尝试获取diffViewerRef
  let attempts = 0;
  while (!diffViewerRef.value && attempts < 10) {
    await new Promise(resolve => setTimeout(resolve, 100));
    attempts++;
  }

  if (diffViewerRef.value) {
    console.log('DiffViewer 组件已加载，开始比对');
    try {
      // 传递选中的内容到DiffViewer
      await diffViewerRef.value.startDiff();

      // 记录比对日志
      await recordComparison();
      ElMessage.success('比对完成！');
    } catch (error: any) {
      console.error('比对失败:', error);
      const errorMessage = error.message || '比对失败，请检查文件格式';
      ElMessage.error(errorMessage);
      showDiffViewer.value = false;
    } finally {
      isComparing.value = false;
    }
  } else {
    console.error('DiffViewer 组件未加载');
    ElMessage.error('比对组件加载失败，请重试');
    isComparing.value = false;
    showDiffViewer.value = false;
  }
};

// 记录比对日志
const recordComparison = async () => {
  try {
    // 检查是否是通过查询参数加载的文件（即从新建比对对话框跳转来的）
    // 如果是，则不需要重复记录，因为已经在对话框中记录过了
    if (route.query.original && route.query.target) {
      console.log('通过查询参数加载的文件，跳过重复记录比对日志');
      return;
    }

    console.log('文件信息检查:', {
      leftFile: leftFile.value,
      rightFile: rightFile.value
    });

    const record = {
      batchId: generateBatchId(),
      originalFilename: leftFile.value?.name || '',
      originalFilePath: leftFile.value?.path || '',
      targetFilename: rightFile.value?.name || '',
      targetFilePath: rightFile.value?.path || '',
    };

    console.log('准备记录比对日志:', record);

    const response = await axios.post('/contract/record', record);
    if (response.data.code !== 200) {
      throw new Error(response.data.message || '记录保存失败');
    }
  } catch (error) {
    console.error('记录日志失败:', error);
    throw error;
  }
};

// 生成批次ID
const generateBatchId = () => {
  return Date.now().toString(36) + Math.random().toString(36).substr(2);
};

// 处理左侧内容选择
const handleLeftContentSelected = (selectedText: string) => {
  leftSelectedContent.value = selectedText;
};

// 处理右侧内容选择
const handleRightContentSelected = (selectedText: string) => {
  rightSelectedContent.value = selectedText;
};

// 返回到选择内容页面
const backToContentSelector = () => {
  showDiffViewer.value = false;
  showContentSelector.value = true;
};

// 从查询参数加载文件
const loadFilesFromQuery = async () => {
  const { original, target, originalFilename, targetFilename, originalContent, targetContent } = route.query;

  if (original && target) {
    try {
      // 获取文件信息
      const [originalResponse, targetResponse] = await Promise.all([
        axios.get('/contract/file/stream', {
          params: { path: original },
          responseType: 'blob'
        }),
        axios.get('/contract/file/stream', {
          params: { path: target },
          responseType: 'blob'
        })
      ]);

      // 从路径中提取文件名（如果没有提供文件名）
      const getFilenameFromPath = (path: string, fallbackName?: string) => {
        if (fallbackName) return fallbackName as string;
        const parts = path.split('/');
        return parts[parts.length - 1] || 'unknown.docx';
      };

      // 创建文件信息
      leftFile.value = {
        name: getFilenameFromPath(original as string, originalFilename as string),
        path: original as string,
        size: originalResponse.data.size || 0
      };

      rightFile.value = {
        name: getFilenameFromPath(target as string, targetFilename as string),
        path: target as string,
        size: targetResponse.data.size || 0
      };

      // 如果有预选内容，设置到状态中
      if (originalContent) {
        leftSelectedContent.value = decodeURIComponent(originalContent as string);
        console.log('加载原文件预选内容:', leftSelectedContent.value?.substring(0, 100) + '...');
      }

      if (targetContent) {
        rightSelectedContent.value = decodeURIComponent(targetContent as string);
        console.log('加载目标文件预选内容:', rightSelectedContent.value?.substring(0, 100) + '...');
      }

      console.log('通过查询参数加载的文件信息:', {
        leftFile: leftFile.value,
        rightFile: rightFile.value,
        hasOriginalContent: !!originalContent,
        hasTargetContent: !!targetContent
      });

      // 显示内容选择器
      showContentSelector.value = true;

    } catch (error) {
      console.error('加载文件失败:', error);
      ElMessage.error('加载文件失败，请重试');
    }
  }
};

// 重新上传
const resetUpload = () => {
  leftFile.value = null;
  rightFile.value = null;
  leftSelectedContent.value = '';
  rightSelectedContent.value = '';
  showDiffViewer.value = false;
  isComparing.value = false;
  showContentSelector.value = false;
  // 清除查询参数
  router.replace({ query: {} });
};

// 组件挂载时检查查询参数
onMounted(() => {
  loadFilesFromQuery();
});

// 监听路由变化，重新加载文件
watch(() => route.query, () => {
  loadFilesFromQuery();
});
</script>

<template>
  <div class="contract-compare">
    <!-- 上传区域 -->
    <div v-if="!showDiffViewer && !showContentSelector" class="upload-container">
      <div class="upload-area">
        <!-- 左侧上传区域 -->
        <div class="upload-box left">
          <el-upload
            class="upload-dragger"
            drag
            :auto-upload="false"
            :show-file-list="false"
            :before-upload="beforeUpload"
            :on-change="handleLeftUpload"
            accept=".docx,.doc"
          >
            <div v-if="!leftFile" class="upload-placeholder">
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">
                拖拽文件到此处或 <em>点击上传</em>
              </div>
              <div class="el-upload__tip">
                只能上传 .docx 或 .doc 文件，且不超过 50MB
              </div>
            </div>
            <div v-else class="file-info">
              <div class="file-icon">
                <DocumentCopy />
              </div>
              <div class="file-details">
                <div class="file-name">{{ leftFile.name }}</div>
                <div class="file-size">{{ (leftFile.size / 1024 / 1024).toFixed(2) }} MB</div>
              </div>
            </div>
          </el-upload>
        </div>

        <!-- 右侧上传区域 -->
        <div class="upload-box right">
          <el-upload
            class="upload-dragger"
            drag
            :auto-upload="false"
            :show-file-list="false"
            :before-upload="beforeUpload"
            :on-change="handleRightUpload"
            accept=".docx,.doc"
          >
            <div v-if="!rightFile" class="upload-placeholder">
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">
                拖拽文件到此处或 <em>点击上传</em>
              </div>
              <div class="el-upload__tip">
                只能上传 .docx 或 .doc 文件，且不超过 50MB
              </div>
            </div>
            <div v-else class="file-info">
              <div class="file-icon">
                <DocumentCopy />
              </div>
              <div class="file-details">
                <div class="file-name">{{ rightFile.name }}</div>
                <div class="file-size">{{ (rightFile.size / 1024 / 1024).toFixed(2) }} MB</div>
              </div>
            </div>
          </el-upload>
        </div>
      </div>

      <div class="action-buttons">
        <el-button
          type="primary"
          size="large"
          :disabled="!leftFile || !rightFile"
          @click="showContentSelector = true"
        >
          <el-icon><Select /></el-icon>
          选择内容
        </el-button>

        <el-button
          v-if="leftFile || rightFile"
          size="large"
          @click="resetUpload"
        >
          重新上传
        </el-button>
      </div>
    </div>

    <!-- 差异展示区域 -->
    <div v-if="showDiffViewer" class="diff-viewer-container">
      <div class="diff-header">
        <div class="header-left">
          <el-button
            size="small"
            @click="backToContentSelector"
            class="back-btn"
          >
            <el-icon><ArrowLeft /></el-icon>
            返回
          </el-button>
          <h3>差异比对结果</h3>
        </div>
      </div>
      <DiffViewer
        ref="diffViewerRef"
        :left-path="leftFile!.path"
        :right-path="rightFile!.path"
        :left-content="leftSelectedContent || null"
        :right-content="rightSelectedContent || null"
      />
    </div>

    <!-- 内容选择器（页面内展开，左右分屏） -->
    <div v-if="showContentSelector" class="content-selector-inline">
      <div class="content-selector-header">
        <div class="header-left">
          <el-button
            size="small"
            @click="showContentSelector = false"
            class="back-btn"
          >
            <el-icon><ArrowLeft /></el-icon>
            返回
          </el-button>
          <h3>选择比对内容</h3>
        </div>
        <div class="header-actions">
          <el-button
            type="primary"
            :loading="isComparing"
            @click="startComparison"
          >
            <el-icon><Select /></el-icon>
            <span v-if="!isComparing">开始比对</span>
            <span v-else>比对中...</span>
          </el-button>
        </div>
      </div>
      <div class="content-selector-body">
        <!-- 左侧文件内容选择 -->
        <div class="selector-panel left">
          <div class="panel-header">
            <h4>{{ leftFile?.name }} (基准文件)</h4>
            <el-tag v-if="leftSelectedContent" type="success" size="small">
              已选择 {{ leftSelectedContent.length }} 字符
            </el-tag>
          </div>
          <FileContentSelector
            v-if="leftFile"
            :file-path="leftFile.path"
            :model-value="leftSelectedContent"
            @update:model-value="handleLeftContentSelected"
            @text-selected="handleLeftContentSelected"
          />
        </div>

        <!-- 分隔线 -->
        <div class="panel-divider"></div>

        <!-- 右侧文件内容选择 -->
        <div class="selector-panel right">
          <div class="panel-header">
            <h4>{{ rightFile?.name }} (比对文件)</h4>
            <el-tag v-if="rightSelectedContent" type="success" size="small">
              已选择 {{ rightSelectedContent.length }} 字符
            </el-tag>
          </div>
          <FileContentSelector
            v-if="rightFile"
            :file-path="rightFile.path"
            :model-value="rightSelectedContent"
            @update:model-value="handleRightContentSelected"
            @text-selected="handleRightContentSelected"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.contract-compare {

  width: 100%;
  box-sizing: border-box;
}

.upload-container {
  margin-top: 10px;
}

.upload-area {
  display: flex;
  gap: 50px;
  margin-bottom: 40px;

  .upload-box {
    flex: 1;
    min-width: 550px;

    h3 {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 20px;
      color: #2c3e50;
    }
  }
}

.upload-dragger {
  width: 100%;
}

.upload-placeholder {
  padding: 40px 20px;
  text-align: center;

  .el-icon--upload {
    font-size: 48px;
    color: #c0c4cc;
    margin-bottom: 16px;
  }
}

.file-info {
  display: flex;
  align-items: center;
  padding: 20px;

  .file-icon {
    font-size: 32px;
    color: #409eff;
    margin-right: 16px;
  }

  .file-details {
    flex: 1;

    .file-name {
      font-weight: 500;
      color: #2c3e50;
      margin-bottom: 4px;
    }

    .file-size {
      font-size: 12px;
      color: #909399;
      margin-bottom: 8px;
    }

    .file-actions {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }
  }
}

.action-buttons {
  display: flex;
  justify-content: center;
  gap: 20px;
}

.diff-viewer-container {
  margin-top: 20px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  overflow: hidden;

  .diff-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px 20px;
    border-bottom: 1px solid #e4e7ed;

    .header-left {
      display: flex;
      align-items: center;
      gap: 12px;

      .back-btn {
        padding: 6px 12px;
      }

      h2 {
        margin: 0;
        color: #2c3e50;
        font-size: 20px;
        font-weight: 600;
      }
    }
  }
}

@media (max-width: 1400px) {
  .upload-area {
    gap: 30px;

    .upload-box {
      min-width: 480px;
    }
  }
}

@media (max-width: 1200px) {
  .upload-area {
    gap: 25px;

    .upload-box {
      min-width: 420px;
    }
  }
}

@media (max-width: 768px) {
  .upload-area {
    flex-direction: column;
    gap: 30px;

    .upload-box {
      min-width: auto;
    }
  }

  .action-buttons {
    flex-direction: column;
    align-items: center;

    .el-button {
      width: 200px;
    }
  }
}

/* 内容选择器页面内展开样式 - 左右分屏 */
.content-selector-inline {
  margin-top: 30px;
  padding: 24px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);

  .content-selector-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    padding-bottom: 16px;
    border-bottom: 1px solid #e4e7ed;

    .header-left {
      display: flex;
      align-items: center;
      gap: 12px;

      .back-btn {
        padding: 6px 12px;
      }

      h3 {
        margin: 0;
        color: #2c3e50;
        font-size: 20px;
        font-weight: 600;
      }
    }

    .header-actions {
      display: flex;
      gap: 12px;
    }
  }

  .content-selector-body {
    display: flex;
    gap: 20px;
    height: 600px;

    .selector-panel {
      flex: 1;
      display: flex;
      flex-direction: column;
      overflow: hidden;
      border: 1px solid #e4e7ed;
      border-radius: 8px;

      .panel-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 16px 20px;
        border-bottom: 1px solid #e4e7ed;
        background: #f8f9fa;
        flex-shrink: 0;

        h4 {
          margin: 0;
          color: #2c3e50;
          font-size: 16px;
          font-weight: 600;
        }
      }

      :deep(.file-content-selector) {
        flex: 1;
        display: flex;
        flex-direction: column;
        overflow: hidden;
        margin-bottom: 0;

        .content-card {
          flex: 1;
          display: flex;
          flex-direction: column;
          overflow: hidden;
          border: none;
          box-shadow: none;
        }

        .content-container {
          flex: 1;
          overflow-y: auto;
        }

        .chapter-list {
          flex: 1;
          overflow-y: auto;
        }
      }
    }
  }
}
</style>
