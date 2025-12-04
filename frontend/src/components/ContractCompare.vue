<script setup lang="ts">
import { ref, reactive } from 'vue';
import { ElMessage, ElUpload } from 'element-plus';
import type { UploadProps, UploadFile } from 'element-plus';
import { UploadFilled, DocumentCopy } from '@element-plus/icons-vue';
import DiffViewer from './DiffViewer.vue';
import axios from 'axios';

interface FileInfo {
  name: string;
  path: string;
  size: number;
}

const leftFile = ref<FileInfo | null>(null);
const rightFile = ref<FileInfo | null>(null);
const isComparing = ref(false);
const showDiffViewer = ref(false);

const diffViewerRef = ref<InstanceType<typeof DiffViewer> | null>(null);

// 文件上传前的验证
const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  const isDocx = file.type === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' ||
                 file.name.toLowerCase().endsWith('.docx');
  const isLt50M = file.size / 1024 / 1024 < 50;

  if (!isDocx) {
    ElMessage.error('只能上传 .docx 格式的文件!');
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

    const response = await axios.post('/api/contract/upload', formData, {
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

  isComparing.value = true;
  showDiffViewer.value = true;

  // 等待DOM更新后调用比对方法
  await new Promise(resolve => setTimeout(resolve, 100));

  if (diffViewerRef.value) {
    try {
      await diffViewerRef.value.startDiff();

      // 记录比对日志
      await recordComparison();
    } catch (error) {
      console.error('比对失败:', error);
      ElMessage.error('比对失败，请检查文件格式');
      showDiffViewer.value = false;
    } finally {
      isComparing.value = false;
    }
  }
};

// 记录比对日志
const recordComparison = async () => {
  try {
    const record = {
      batchId: generateBatchId(),
      originalFilename: leftFile.value!.name,
      originalFilePath: leftFile.value!.path,
      targetFilename: rightFile.value!.name,
      targetFilePath: rightFile.value!.path,
    };

    await axios.post('/api/contract/record', record);
  } catch (error) {
    console.error('记录日志失败:', error);
  }
};

// 生成批次ID
const generateBatchId = () => {
  return Date.now().toString(36) + Math.random().toString(36).substr(2);
};

// 重新上传
const resetUpload = () => {
  leftFile.value = null;
  rightFile.value = null;
  showDiffViewer.value = false;
  isComparing.value = false;
};
</script>

<template>
  <div class="contract-compare">
    <div class="header">
      <h1>在线合同智能比对系统</h1>
      <p>上传两个Word文档，系统将自动识别并高亮显示差异</p>
    </div>

    <div v-if="!showDiffViewer" class="upload-container">
      <div class="upload-area">
        <!-- 左侧上传区域 -->
        <div class="upload-box left">
          <h3>
            <DocumentCopy />
            基准文件（原件）
          </h3>
          <el-upload
            class="upload-dragger"
            drag
            :auto-upload="false"
            :show-file-list="false"
            :before-upload="beforeUpload"
            :on-change="handleLeftUpload"
            accept=".docx"
          >
            <div v-if="!leftFile" class="upload-placeholder">
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">
                拖拽文件到此处或 <em>点击上传</em>
              </div>
              <div class="el-upload__tip">
                只能上传 .docx 文件，且不超过 50MB
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
          <h3>
            <DocumentCopy />
            比对文件（目标件）
          </h3>
          <el-upload
            class="upload-dragger"
            drag
            :auto-upload="false"
            :show-file-list="false"
            :before-upload="beforeUpload"
            :on-change="handleRightUpload"
            accept=".docx"
          >
            <div v-if="!rightFile" class="upload-placeholder">
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">
                拖拽文件到此处或 <em>点击上传</em>
              </div>
              <div class="el-upload__tip">
                只能上传 .docx 文件，且不超过 50MB
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
          :loading="isComparing"
          :disabled="!leftFile || !rightFile"
          @click="startComparison"
        >
          <span v-if="!isComparing">开始比对</span>
          <span v-else>比对中...</span>
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
        <h2>差异比对结果</h2>
        <el-button @click="showDiffViewer = false">返回上传</el-button>
      </div>
      <DiffViewer
        ref="diffViewerRef"
        :left-path="leftFile!.path"
        :right-path="rightFile!.path"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.contract-compare {
  max-width: 1600px;
  margin: 0 auto;
  padding: 20px;
}

.header {
  text-align: center;
  margin-bottom: 40px;

  h1 {
    color: #2c3e50;
    margin-bottom: 10px;
  }

  p {
    color: #7f8c8d;
    font-size: 16px;
  }
}

.upload-container {
  margin-top: 40px;
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
  width: calc(100vw - 40px); /* 铺满屏幕宽度，减去页面padding */
  margin-left: -20px; /* 抵消父容器的padding */
  margin-right: -20px; /* 抵消父容器的padding */
  padding: 0 20px; /* 重新添加内边距 */

  .diff-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    padding-bottom: 10px;
    border-bottom: 1px solid #e4e7ed;

    h2 {
      margin: 0;
      color: #2c3e50;
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
</style>