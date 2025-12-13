<template>
  <div class="file-content-selector">
    <el-card class="content-card">

      <!-- 加载状态 -->
      <div v-if="loading" class="loading-container">
        <el-skeleton :rows="15" animated />
        <div class="loading-text">正在解析文件内容...</div>
      </div>

      <!-- 文件内容显示 -->
      <div v-else-if="content" class="content-container">
        <!-- 文本选择区域 -->
        <div
          ref="contentDisplayRef"
          class="content-display"
          @mouseup="handleTextSelection"
          @selectstart="handleSelectionStart"
        >
          <div class="content-text" v-html="highlightedContent"></div>
        </div>

        <!-- 选择结果 -->
        <div v-if="selectedText" class="selection-result">
          <div class="selection-header">
            <span class="selection-title">已选择内容：</span>
            <el-button
              type="text"
              size="small"
              @click="clearSelection"
              class="clear-btn"
            >
              <el-icon><Close /></el-icon>
              清除选择
            </el-button>
          </div>
          <div class="selected-text-preview">{{ selectedText }}</div>
          <div class="selection-info">
            <el-text size="small" type="info">
              已选择 {{ selectedText.length }} 个字符
            </el-text>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else-if="!filePath" class="empty-state">
        <el-empty description="请先选择文件" />
      </div>

      <div v-else class="empty-state">
        <el-empty description="文件内容解析失败" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Close } from '@element-plus/icons-vue'
import axios from 'axios'

interface Props {
  filePath?: string | null
  modelValue: string // 选中的文本内容
}

interface Emits {
  (e: 'update:modelValue', value: string): void
  (e: 'textSelected', text: string): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const loading = ref(false)
const content = ref('')
const rawContent = ref('')
const selectedText = ref('')

// 加载文件内容
const loadFileContent = async (filePath: string) => {
  if (!filePath) return

  loading.value = true
  try {
    // 判断文件格式
    const isDoc = filePath.toLowerCase().endsWith('.doc')
    const isDocx = filePath.toLowerCase().endsWith('.docx')

    if (isDocx) {
      // 使用mammoth.js解析.docx文件
      const response = await axios.get('/api/contract/file/stream', {
        params: { path: filePath },
        responseType: 'arraybuffer'
      })

      // 使用mammoth.js解析，获取纯文本和HTML两种格式
      const arrayBuffer = response.data
      const mammoth = await import('mammoth')

      // 获取纯文本版本用于选择
      const textResult = await mammoth.extractRawText({ arrayBuffer })
      rawContent.value = textResult.value

      // 获取HTML版本用于显示
      const htmlResult = await mammoth.convertToHtml({
        arrayBuffer,
        includeDefaultStyleMap: true,
        styleMap: [
          "p[style-name='Heading 1'] => h1:fresh",
          "p[style-name='Heading 2'] => h2:fresh",
          "p[style-name='Heading 3'] => h3:fresh",
          "p[style-name='Heading 4'] => h4:fresh",
          "p[style-name='Heading 5'] => h5:fresh",
          "p[style-name='Heading 6'] => h6:fresh"
        ]
      })
      content.value = htmlResult.value

    } else if (isDoc) {
      // 处理.doc文件
      const response = await axios.get('/api/contract/file/stream', {
        params: { path: filePath },
        responseType: 'arraybuffer'
      })

      // 解析.doc文件的文本内容
      const docText = await extractDocText(response.data)
      rawContent.value = docText

      // 将文本转换为HTML用于显示
      content.value = docText
        .split('\n')
        .map(line => line.trim() ? `<p style="margin: 8px 0; line-height: 1.6;">${escapeHtml(line)}</p>` : '<br/>')
        .join('')

    } else {
      throw new Error('不支持的文件格式')
    }

    ElMessage.success('文件内容加载完成')
  } catch (error) {
    console.error('加载文件内容失败:', error)
    ElMessage.error('文件内容加载失败: ' + (error as Error).message)
  } finally {
    loading.value = false
  }
}

// 提取.doc文件文本内容
const extractDocText = async (arrayBuffer: ArrayBuffer): Promise<string> => {
  try {
    // 方法1: 尝试检测并解析不同的文本编码
    const uint8Array = new Uint8Array(arrayBuffer)

    // 检测可能的文本编码
    const encoding = detectDocEncoding(uint8Array)
    let extractedText = ''

    if (encoding === 'utf-16le' || encoding === 'utf-16be') {
      // 处理Unicode编码
      extractedText = extractUnicodeText(uint8Array, encoding)
    } else if (encoding === 'ascii') {
      // 处理ASCII/ANSI编码
      extractedText = extractAsciiText(uint8Array)
    } else {
      // 通用文本提取
      extractedText = extractGeneralText(uint8Array)
    }

    // 如果提取的文本太少或包含太多乱码，使用备用方法
    if (!isReasonableText(extractedText)) {
      extractedText = await fallbackDocExtraction(arrayBuffer)
    }

    return cleanExtractedText(extractedText)
  } catch (error) {
    console.error('解析.doc文件失败:', error)
    // 如果解析失败，返回用户友好的提示
    return `DOC文件解析提示

文件大小: ${(arrayBuffer.byteLength / 1024).toFixed(2)} KB

由于.doc格式复杂性和浏览器限制，当前无法直接解析此文件内容。

建议解决方案：
1. 使用Microsoft Word将文件另存为.docx格式
2. 使用在线转换工具将.doc转换为.docx
3. 将文件内容复制粘贴到新的文本文件中

.docx格式可以完美支持内容预览和选择功能。`
  }
}

// 检测.doc文件可能的编码
const detectDocEncoding = (uint8Array: Uint8Array): string => {
  // 检查BOM (Byte Order Mark)
  if (uint8Array.length >= 2) {
    // UTF-16 LE BOM
    if (uint8Array[0] === 0xFF && uint8Array[1] === 0xFE) {
      return 'utf-16le'
    }
    // UTF-16 BE BOM
    if (uint8Array[0] === 0xFE && uint8Array[1] === 0xFF) {
      return 'utf-16be'
    }
  }

  // 检查是否可能是UTF-16 (没有BOM)
  let likelyUtf16 = 0
  let asciiCount = 0

  for (let i = 0; i < Math.min(1000, uint8Array.length); i += 2) {
    if (uint8Array[i] === 0 && uint8Array[i + 1] > 0) {
      likelyUtf16++
    } else if (uint8Array[i] >= 32 && uint8Array[i] <= 126) {
      asciiCount++
    }
  }

  if (likelyUtf16 > asciiCount / 10) {
    return 'utf-16le' // 默认假设是小端序
  }

  return 'ascii'
}

// 提取Unicode文本
const extractUnicodeText = (uint8Array: Uint8Array, encoding: string): string => {
  let text = ''
  const isLe = encoding === 'utf-16le'

  for (let i = 0; i < uint8Array.length - 1; i += 2) {
    const charCode = isLe
      ? uint8Array[i] | (uint8Array[i + 1] << 8)
      : (uint8Array[i] << 8) | uint8Array[i + 1]

    if (charCode === 0) break
    if (charCode >= 32 && charCode <= 126) { // ASCII字符
      text += String.fromCharCode(charCode)
    } else if (charCode >= 0x4E00 && charCode <= 0x9FFF) { // 中文字符范围
      text += String.fromCharCode(charCode)
    } else if (charCode === 10 || charCode === 13) { // 换行符
      text += '\n'
    }
  }

  return text
}

// 提取ASCII文本
const extractAsciiText = (uint8Array: Uint8Array): string => {
  let text = ''

  for (let i = 0; i < uint8Array.length; i++) {
    const byte = uint8Array[i]

    if ((byte >= 32 && byte <= 126) || (byte >= 128 && byte <= 255)) {
      text += String.fromCharCode(byte)
    } else if (byte === 10 || byte === 13) {
      text += '\n'
    } else if (byte === 9) {
      text += ' '
    }
  }

  return text
}

// 通用文本提取
const extractGeneralText = (uint8Array: Uint8Array): string => {
  let text = ''
  let wordStart = false

  for (let i = 0; i < uint8Array.length; i++) {
    const byte = uint8Array[i]

    // 开始可能的文本区域
    if (byte >= 65 && byte <= 90 || byte >= 97 && byte <= 122) { // A-Z, a-z
      wordStart = true
      text += String.fromCharCode(byte)
    } else if (wordStart && byte >= 32 && byte <= 126) {
      text += String.fromCharCode(byte)
    } else if (byte === 10 || byte === 13) {
      text += '\n'
      wordStart = false
    } else if (byte === 9 || byte === 32) {
      text += ' '
    } else {
      wordStart = false
    }
  }

  return text
}

// 检查提取的文本是否合理
const isReasonableText = (text: string): boolean => {
  if (!text || text.length < 10) return false

  // 检查是否包含合理的字符
  const printableChars = text.replace(/[\s\r\n]/g, '').length
  const totalChars = text.length

  // 至少30%是可打印字符
  if (printableChars / totalChars < 0.3) return false

  // 检查是否包含常用中文字符或英文单词
  const hasChinese = /[\u4e00-\u9fff]/.test(text)
  const hasEnglish = /[a-zA-Z]{3,}/.test(text)

  return hasChinese || hasEnglish
}

// 备用的.doc提取方法
const fallbackDocExtraction = async (arrayBuffer: ArrayBuffer): Promise<string> => {
  try {
    // 尝试寻找.doc文件中的文本块
    const uint8Array = new Uint8Array(arrayBuffer)
    let text = ''

    // 查找连续的文本区域
    for (let i = 0; i < uint8Array.length - 50; i++) {
      // 查找可能的文本开始位置
      if (uint8Array[i] >= 32 && uint8Array[i] <= 126) {
        let segment = ''
        let j = i

        // 提取连续的可读字符
        while (j < uint8Array.length && j < i + 1000) {
          const byte = uint8Array[j]

          if ((byte >= 32 && byte <= 126) || (byte >= 128 && byte <= 255)) {
            segment += String.fromCharCode(byte)
          } else if (byte === 10 || byte === 13) {
            segment += '\n'
          } else if (byte === 9) {
            segment += ' '
          } else {
            break
          }
          j++
        }

        if (segment.length > 20 && /[a-zA-Z\u4e00-\u9fff]/.test(segment)) {
          text += segment + '\n'
          i = j
        }
      }
    }

    return text || '无法提取可读文本内容'
  } catch (error) {
    console.error('备用解析方法失败:', error)
    return '文本提取失败'
  }
}

// 清理提取的文本
const cleanExtractedText = (text: string): string => {
  return text
    // 移除过多的控制字符
    .replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g, '')
    // 合并多个空格
    .replace(/[ \t]+/g, ' ')
    // 合并多个换行符
    .replace(/\n{3,}/g, '\n\n')
    // 移除开头的空白字符
    .trim()
    // 限制长度，避免内存问题
    .substring(0, 50000) // 最多50K字符
}

// HTML转义
const escapeHtml = (text: string): string => {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

// 监听文件路径变化
watch(() => props.filePath, (newFilePath) => {
  if (newFilePath) {
    loadFileContent(newFilePath)
  } else {
    clearAll()
  }
}, { immediate: true })

// 从HTML提取纯文本
const extractTextFromHtml = (html: string): string => {
  const tempDiv = document.createElement('div')
  tempDiv.innerHTML = html
  return tempDiv.textContent || tempDiv.innerText || ''
}


// 计算高亮内容
const highlightedContent = computed(() => {
  // 直接返回原始内容，高亮通过CSS实现
  return content.value
})


// 处理选择开始
const handleSelectionStart = () => {
  // 选择开始时不做处理，保持浏览器默认行为
}

// 规范化文本：移除多余的空白字符，统一换行符
const normalizeText = (text: string): string => {
  return text
    .replace(/\r\n/g, '\n')  // 统一换行符
    .replace(/\r/g, '\n')   // 处理旧Mac换行符
    .replace(/\t/g, ' ')    // 制表符转为空格
    .replace(/[ \u00A0]+/g, ' ')  // 多个空格和NBSP转为单个空格
    .trim()
}

// 处理文本选择
const handleTextSelection = () => {
  // 延迟一下获取选择内容，确保选择完成
  setTimeout(() => {
    const selection = window.getSelection()
    if (selection && selection.toString().trim()) {
      // 获取选择的原始内容
      let selectedContent = selection.toString()

      console.log('选择处理:', {
        original: JSON.stringify(selection.toString()),
        raw: selectedContent
      })

      selectedText.value = selectedContent
      emit('update:modelValue', selectedContent)
      emit('textSelected', selectedContent)

      // 不清除选择，让用户看到高亮效果
      // 选择状态会通过CSS ::selection 显示
    }
  }, 50)
}

// 清除选择
const clearSelection = () => {
  selectedText.value = ''
  emit('update:modelValue', '')
  emit('textSelected', '')

  // 清除浏览器选择状态
  window.getSelection()?.removeAllRanges()
}

// 清除所有内容
const clearAll = () => {
  content.value = ''
  rawContent.value = ''
  selectedText.value = ''
  loading.value = false
}

const contentDisplayRef = ref<HTMLElement>()

// 处理选择变化事件
const handleSelectionChange = () => {
  const selection = window.getSelection()

  if (selection && selection.toString().trim() && contentDisplayRef.value) {
    // 获取选择范围的节点
    const range = selection.getRangeAt(0)
    const selectionContainer = range.commonAncestorContainer

    // 检查选择是否在当前组件内
    if (contentDisplayRef.value.contains(selectionContainer) ||
        contentDisplayRef.value.contains(selectionContainer.parentElement)) {
      const selectedContent = selection.toString()
      selectedText.value = selectedContent
      emit('update:modelValue', selectedContent)
      emit('textSelected', selectedContent)
    }
  }
}

// 组件挂载时添加选择变化监听
onMounted(() => {
  document.addEventListener('selectionchange', handleSelectionChange)
})

// 组件卸载时移除监听
onUnmounted(() => {
  document.removeEventListener('selectionchange', handleSelectionChange)
})


</script>

<style scoped>
.file-content-selector {
  margin-bottom: 20px;
}

.content-card {
  min-height: 400px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  color: #2c3e50;
}

.filename {
  color: #667eea;
}

.loading-container {
  padding: 20px;
  text-align: center;
}

.loading-text {
  margin-top: 16px;
  color: #7f8c8d;
  font-size: 14px;
}

.content-container {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
}

.content-display {
  max-height: 300px;
  overflow-y: auto;
  padding: 20px;
  cursor: text;
  user-select: text;
  line-height: 1.8;
  font-size: 14px;
  color: #2c3e50;
}

.content-text {
  white-space: pre-wrap;
  word-wrap: break-word;
}

/* 增强文本选择的高亮效果 */
.content-display {
  &::selection {
    background-color: #fff2e8;
    color: #e6a23c;
    text-shadow: none;
  }

  &::-moz-selection {
    background-color: #fff2e8;
    color: #e6a23c;
    text-shadow: none;
  }
}

:deep(mark) {
  background-color: #fff2e8;
  color: #e6a23c;
  padding: 2px 4px;
  border-radius: 4px;
  font-weight: 500;
}

.selection-result {
  border-top: 1px solid #f0f2f5;
  padding: 16px;
  background: #f8f9fa;
}

.selection-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.selection-title {
  font-weight: 500;
  color: #2c3e50;
  font-size: 14px;
}

.clear-btn {
  color: #909399;
}

.clear-btn:hover {
  color: #409eff;
}

.selected-text-preview {
  max-height: 100px;
  overflow-y: auto;
  padding: 12px;
  background: white;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-wrap: break-word;
  margin-bottom: 8px;
}

.selection-info {
  text-align: right;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .card-header {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }
}
</style>