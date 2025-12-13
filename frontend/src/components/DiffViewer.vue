<script setup lang="ts">
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import axios from 'axios';
import mammoth from 'mammoth';
import DiffMatchPatch from 'diff-match-patch';

const props = defineProps<{
  leftPath: string;
  rightPath: string;
  leftContent?: string | null;
  rightContent?: string | null;
}>();

const leftHtml = ref('');
const rightHtml = ref('');
const isLoading = ref(false);
const diffPositions = ref<Array<{elements: Element[], panel: string}>>([]);
let currentDiffIndex = 0;

// 获取文档文本内容
const getDocumentText = async (filePath: string): Promise<string> => {
  const isDoc = filePath.toLowerCase().endsWith('.doc')
  const isDocx = filePath.toLowerCase().endsWith('.docx')

  if (isDocx) {
    // 使用mammoth.js解析.docx文件
    const response = await axios.get(`/api/contract/file/stream?path=${filePath}`, {
      responseType: 'arraybuffer'
    });
    return (await mammoth.extractRawText({
      arrayBuffer: response.data
    })).value;
  } else if (isDoc) {
    // 使用与FileContentSelector相同的.doc文件解析方法
    const response = await axios.get(`/api/contract/file/stream?path=${filePath}`, {
      responseType: 'arraybuffer'
    });
    return extractDocText(response.data);
  } else {
    throw new Error('不支持的文件格式');
  }
};

// 提取.doc文件文本内容
const extractDocText = async (arrayBuffer: ArrayBuffer): Promise<string> => {
  try {
    const uint8Array = new Uint8Array(arrayBuffer)
    const encoding = detectDocEncoding(uint8Array)
    let extractedText = ''

    if (encoding === 'utf-16le' || encoding === 'utf-16be') {
      extractedText = extractUnicodeText(uint8Array, encoding)
    } else if (encoding === 'ascii') {
      extractedText = extractAsciiText(uint8Array)
    } else {
      extractedText = extractGeneralText(uint8Array)
    }

    if (!isReasonableText(extractedText)) {
      extractedText = await fallbackDocExtraction(arrayBuffer)
    }

    return cleanExtractedText(extractedText)
  } catch (error) {
    console.error('解析.doc文件失败:', error)
    return `DOC文件解析提示：无法提取文本内容，建议转换为.docx格式。`
  }
}

// 检测.doc文件可能的编码
const detectDocEncoding = (uint8Array: Uint8Array): string => {
  if (uint8Array.length >= 2) {
    if (uint8Array[0] === 0xFF && uint8Array[1] === 0xFE) {
      return 'utf-16le'
    }
    if (uint8Array[0] === 0xFE && uint8Array[1] === 0xFF) {
      return 'utf-16be'
    }
  }

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
    return 'utf-16le'
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
    if (charCode >= 32 && charCode <= 126) {
      text += String.fromCharCode(charCode)
    } else if (charCode >= 0x4E00 && charCode <= 0x9FFF) {
      text += String.fromCharCode(charCode)
    } else if (charCode === 10 || charCode === 13) {
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

    if (byte >= 65 && byte <= 90 || byte >= 97 && byte <= 122) {
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

  const printableChars = text.replace(/[\s\r\n]/g, '').length
  const totalChars = text.length

  if (printableChars / totalChars < 0.3) return false

  const hasChinese = /[\u4e00-\u9fff]/.test(text)
  const hasEnglish = /[a-zA-Z]{3,}/.test(text)

  return hasChinese || hasEnglish
}

// 备用的.doc提取方法
const fallbackDocExtraction = async (arrayBuffer: ArrayBuffer): Promise<string> => {
  try {
    const uint8Array = new Uint8Array(arrayBuffer)
    let text = ''

    for (let i = 0; i < uint8Array.length - 50; i++) {
      if (uint8Array[i] >= 32 && uint8Array[i] <= 126) {
        let segment = ''
        let j = i

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
    .replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g, '')
    .replace(/[ \t]+/g, ' ')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
    .substring(0, 50000)
}

// 开始差异比对
const startDiff = async () => {
  isLoading.value = true;
  try {
    let textLeft: string;
    let textRight: string;

    // 1. 获取文本内容
    if (props.leftContent) {
      // 使用提供的部分内容
      textLeft = props.leftContent;
    } else {
      textLeft = await getDocumentText(props.leftPath);
    }

    if (props.rightContent) {
      // 使用提供的部分内容
      textRight = props.rightContent;
    } else {
      textRight = await getDocumentText(props.rightPath);
    }

    // 2. 计算差异
    const dmp = new (DiffMatchPatch as any)();
    const diffs = dmp.diff_main(textLeft, textRight);
    dmp.diff_cleanupSemantic(diffs);

    // 3. 生成 HTML
    let lHtml = '';
    let rHtml = '';

    diffs.forEach(([op, text]: [number, string]) => {
      // 转义HTML特殊字符并将换行符转为<br/>
      const safeText = text
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/\n/g, '<br/>');

      if (op === -1) { // DELETE
        lHtml += `<span class="diff-del">${safeText}</span>`;
      } else if (op === 1) { // INSERT
        rHtml += `<span class="diff-add">${safeText}</span>`;
      } else { // EQUAL
        lHtml += `<span class="diff-equal">${safeText}</span>`;
        rHtml += `<span class="diff-equal">${safeText}</span>`;
      }
    });

    leftHtml.value = lHtml;
    rightHtml.value = rHtml;

    // 5. 设置同步滚动
    setupSyncScroll();

    // 6. 设置差异位置
    setTimeout(() => {
      setupDiffPositions();
    }, 100);

  } catch (err) {
    console.error('比对失败:', err);
    ElMessage.error('比对失败，请检查文件格式');
    throw err;
  } finally {
    isLoading.value = false;
  }
};

// 设置同步滚动
const setupSyncScroll = () => {
  setTimeout(() => {
    const leftPanel = document.getElementById('left-panel');
    const rightPanel = document.getElementById('right-panel');

    if (leftPanel && rightPanel) {
      const syncScroll = (source: HTMLElement, target: HTMLElement) => {
        source.addEventListener('scroll', () => {
          target.scrollTop = source.scrollTop;
        });
      };

      syncScroll(leftPanel, rightPanel);
      syncScroll(rightPanel, leftPanel);
    }
  }, 100);
};

// 设置差异位置
const setupDiffPositions = () => {
  const leftPanel = document.getElementById('left-panel');
  const rightPanel = document.getElementById('right-panel');

  if (!leftPanel || !rightPanel) {
    console.log('面板未找到');
    return;
  }

  const diffs: Array<{elements: Element[], panel: string}> = [];

  // 获取所有差异元素并按实际DOM位置排序
  const allLeftDels = Array.from(leftPanel.querySelectorAll('.diff-del'));
  const allRightAdds = Array.from(rightPanel.querySelectorAll('.diff-add'));

  console.log('找到差异 - 左侧删除:', allLeftDels.length, '右侧新增:', allRightAdds.length);

  // 收集所有差异元素，记录它们的实际DOM位置
  const allDiffs: Array<{element: Element, panel: 'left' | 'right', top: number}> = [];

  // 收集左侧差异
  allLeftDels.forEach(element => {
    const rect = element.getBoundingClientRect();
    allDiffs.push({
      element,
      panel: 'left',
      top: rect.top
    });
  });

  // 收集右侧差异
  allRightAdds.forEach(element => {
    const rect = element.getBoundingClientRect();
    allDiffs.push({
      element,
      panel: 'right',
      top: rect.top
    });
  });

  // 按实际屏幕位置排序
  allDiffs.sort((a, b) => a.top - b.top);

  console.log('差异元素按位置排序完成，前5个位置:', allDiffs.slice(0, 5).map(d => d.top));

  // 分组相近的差异元素（30px范围内认为是同一差异块）
  const groupedDiffs: {elements: Element[], panel: string}[] = [];
  let currentGroup: {elements: Element[], panel: string} | null = null;

  allDiffs.forEach((diff, index) => {
    if (!currentGroup) {
      // 创建第一个组
      currentGroup = {
        elements: [diff.element],
        panel: diff.panel
      };
    } else {
      // 检查是否与当前组相近
      const lastElementInGroup = currentGroup.elements[currentGroup.elements.length - 1];
      const lastRect = lastElementInGroup.getBoundingClientRect();
      const distance = Math.abs(diff.top - lastRect.top);

      if (distance < 30) {
        // 相近，加入当前组
        currentGroup.elements.push(diff.element);
      } else {
        // 不相近，保存当前组并创建新组
        groupedDiffs.push(currentGroup);
        currentGroup = {
          elements: [diff.element],
          panel: diff.panel
        };
      }
    }

    // 最后一个元素需要保存
    if (index === allDiffs.length - 1 && currentGroup) {
      groupedDiffs.push(currentGroup);
    }
  });

  diffPositions.value = groupedDiffs;
  currentDiffIndex = 0;

  console.log('差异块设置完成，总数:', groupedDiffs.length);

  // 如果有差异，自动定位到第一个
  if (groupedDiffs.length > 0) {
    setTimeout(() => {
      scrollToDiff(0);
    }, 500);
  }
};

// 计算元素的绝对滚动位置
const calculateAbsoluteScrollPosition = (panel: HTMLElement, element: HTMLElement): number => {
  let offsetTop = 0;
  let currentElement: Element | null = element;

  // 向上遍历到面板，计算累积偏移量
  while (currentElement && currentElement !== panel) {
    if (currentElement instanceof HTMLElement) {
      offsetTop += currentElement.offsetTop;
    }
    currentElement = currentElement.parentElement;
  }

  return offsetTop;
};

// 导航到下一个差异
const nextDiff = () => {
  console.log('点击下一个差异，当前:', currentDiffIndex, '总数:', diffPositions.value.length);
  if (diffPositions.value.length === 0) return;

  currentDiffIndex = (currentDiffIndex + 1) % diffPositions.value.length;
  scrollToDiff(currentDiffIndex);
};

// 导航到上一个差异
const prevDiff = () => {
  console.log('点击上一个差异，当前:', currentDiffIndex, '总数:', diffPositions.value.length);
  if (diffPositions.value.length === 0) return;

  currentDiffIndex = currentDiffIndex === 0 ? diffPositions.value.length - 1 : currentDiffIndex - 1;
  scrollToDiff(currentDiffIndex);
};

// 滚动到指定差异位置
const scrollToDiff = (index: number) => {
  if (index < 0 || index >= diffPositions.value.length) return;

  const diff = diffPositions.value[index];

  if (!diff.elements || diff.elements.length === 0) return;

  const leftPanel = document.getElementById('left-panel');
  const rightPanel = document.getElementById('right-panel');

  if (!leftPanel || !rightPanel) {
    console.log('面板未找到');
    return;
  }

  console.log('定位到差异块', index + 1, '/', diffPositions.value.length);

  // 获取第一个差异元素的实时位置
  const firstElement = diff.elements[0];
  const rect = firstElement.getBoundingClientRect();

  // 获取面板的实时位置
  const leftPanelRect = leftPanel.getBoundingClientRect();
  const currentScrollTop = leftPanel.scrollTop;

  // 计算目标滚动位置：元素相对于面板的偏移量 + 当前滚动位置 - 显示偏移
  const elementRelativeTop = rect.top - leftPanelRect.top;
  const targetScrollTop = currentScrollTop + elementRelativeTop - 100; // 让元素显示在视口上方100px处

  console.log('当前滚动位置:', currentScrollTop, '元素相对位置:', elementRelativeTop, '目标位置:', targetScrollTop);

  // 直接跳转到计算的位置
  leftPanel.scrollTop = Math.max(0, targetScrollTop);
  rightPanel.scrollTop = Math.max(0, targetScrollTop);

  // 高亮当前差异块的所有元素
  highlightDiffElements(diff.elements);
};

// 计算元素的滚动位置
const calculateScrollPosition = (panel: HTMLElement, element: HTMLElement): number => {
  let offsetTop = 0;
  let currentElement: Element | null = element;

  // 向上遍历父元素计算偏移量
  while (currentElement && currentElement !== panel) {
    if (currentElement instanceof HTMLElement) {
      offsetTop += currentElement.offsetTop;
    }
    currentElement = currentElement.parentElement;
  }

  // 计算最终滚动位置（让元素居中显示，但不要太靠上）
  const panelHeight = panel.clientHeight;
  const finalScrollTop = Math.max(0, offsetTop - 100); // 距离顶部100px

  return finalScrollTop;
};

// 高亮差异元素
const highlightDiffElements = (elements: Element[]) => {
  // 高亮所有差异元素
  elements.forEach(element => {
    element.classList.add('diff-highlight');
  });

  // 2秒后移除高亮
  setTimeout(() => {
    elements.forEach(element => {
      element.classList.remove('diff-highlight');
    });
  }, 2000);
};

defineExpose({ startDiff });
</script>

<template>
  <div class="diff-viewer">
    <div v-if="isLoading" class="loading-container">
      <el-icon class="loading-icon">
        <svg class="circular" viewBox="25 25 50 50">
          <circle class="path" cx="50" cy="50" r="20" fill="none" stroke="currentColor" stroke-width="4"></circle>
        </svg>
      </el-icon>
      <p>正在解析文件并计算差异...</p>
    </div>

    <div v-else class="diff-content">
      <div class="panels">
        <!-- 左侧面板 -->
        <div class="panel">
          <div class="panel-header">
            <h3>原文件</h3>
            <div class="panel-controls">
              <span class="diff-legend">
                <span class="legend-item delete">删除</span>
                <span class="legend-item equal">相同</span>
              </span>
              <div class="nav-buttons">
                <button class="nav-btn prev" @click="prevDiff" :disabled="diffPositions.length === 0" title="上一个差异">
                  ↑
                </button>
                <span class="diff-counter" v-if="diffPositions.length > 0">
                  {{ currentDiffIndex + 1 }} / {{ diffPositions.length }}
                </span>
                <button class="nav-btn next" @click="nextDiff" :disabled="diffPositions.length === 0" title="下一个差异">
                  ↓
                </button>
              </div>
            </div>
          </div>
          <div id="left-panel" class="panel-content" v-html="leftHtml"></div>
        </div>

        <!-- 右侧面板 -->
        <div class="panel">
          <div class="panel-header">
            <h3>目标文件</h3>
            <div class="panel-controls">
              <span class="diff-legend">
                <span class="legend-item add">新增</span>
                <span class="legend-item equal">相同</span>
              </span>
              <div class="nav-buttons">
                <button class="nav-btn prev" @click="prevDiff" :disabled="diffPositions.length === 0" title="上一个差异">
                  ↑
                </button>
                <span class="diff-counter" v-if="diffPositions.length > 0">
                  {{ currentDiffIndex + 1 }} / {{ diffPositions.length }}
                </span>
                <button class="nav-btn next" @click="nextDiff" :disabled="diffPositions.length === 0" title="下一个差异">
                  ↓
                </button>
              </div>
            </div>
          </div>
          <div id="right-panel" class="panel-content" v-html="rightHtml"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.diff-viewer {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
  width: 100%; /* 确保容器铺满宽度 */
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;

  .loading-icon {
    font-size: 48px;
    color: #409eff;
    margin-bottom: 16px;

    .circular {
      animation: loading-rotate 2s linear infinite;
      width: 1em;
      height: 1em;
    }

    .path {
      animation: loading-dash 1.5s ease-in-out infinite;
      stroke-dasharray: 90, 150;
      stroke-dashoffset: 0;
      stroke-linecap: round;
    }
  }

  p {
    color: #606266;
    font-size: 16px;
    margin: 0;
  }
}

@keyframes loading-rotate {
  100% {
    transform: rotate(360deg);
  }
}

@keyframes loading-dash {
  0% {
    stroke-dasharray: 1, 150;
    stroke-dashoffset: 0;
  }
  50% {
    stroke-dasharray: 90, 150;
    stroke-dashoffset: -35;
  }
  100% {
    stroke-dasharray: 90, 150;
    stroke-dashoffset: -124;
  }
}

.diff-content {
  width: 100%; /* 确保内容区域铺满 */

  .panels {
    display: flex;
    height: calc(100vh - 200px); /* 自适应屏幕高度，减去头部和操作区域的高度 */
    min-height: 500px; /* 最小高度 */
    gap: 0;
    width: 100%; /* 确保面板容器铺满 */
  }

  .panel {
    flex: 1;
    min-width: 400px; /* 增加最小宽度要求 */
    max-width: none; /* 移除最大宽度限制 */
    display: flex;
    flex-direction: column;
    border-right: 1px solid #e4e7ed;

    &:last-child {
      border-right: none;
    }
  }

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px 20px;
    background-color: #f8f9fa;
    border-bottom: 1px solid #e4e7ed;

    h3 {
      margin: 0;
      font-size: 16px;
      color: #2c3e50;
    }

    .panel-controls {
      display: flex;
      align-items: center;
      gap: 16px;
    }

    .diff-legend {
      display: flex;
      gap: 12px;
      font-size: 12px;

      .legend-item {
        padding: 4px 8px;
        border-radius: 4px;
        font-weight: 500;

        &.delete {
          background-color: #ffeef0;
          color: #f56c6c;
        }

        &.add {
          background-color: #e6ffec;
          color: #67c23a;
        }

        &.equal {
          background-color: #f5f5f5;
          color: #909399;
        }
      }
    }

    .nav-buttons {
      display: flex;
      align-items: center;
      gap: 8px;

      .nav-btn {
        width: 32px;
        height: 32px;
        border: 1px solid #dcdfe6;
        background-color: #fff;
        color: #606266;
        border-radius: 4px;
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 16px;
        font-weight: bold;
        transition: all 0.2s;

        &:hover:not(:disabled) {
          background-color: #409eff;
          color: #fff;
          border-color: #409eff;
        }

        &:disabled {
          cursor: not-allowed;
          opacity: 0.5;
        }
      }

      .diff-counter {
        font-size: 12px;
        color: #606266;
        font-weight: 500;
        min-width: 50px;
        text-align: center;
      }
    }
  }

  .panel-content {
    flex: 1;
    padding: 25px;
    overflow-y: auto;
    line-height: 1.8;
    font-size: 15px;
    white-space: pre-wrap;
    word-break: break-word;

    // 自定义滚动条样式
    &::-webkit-scrollbar {
      width: 6px;
    }

    &::-webkit-scrollbar-track {
      background: #f1f1f1;
    }

    &::-webkit-scrollbar-thumb {
      background: #c0c4cc;
      border-radius: 3px;
    }

    &::-webkit-scrollbar-thumb:hover {
      background: #909399;
    }
  }
}

// 差异样式
:deep(.diff-del) {
  background-color: #ffeef0;
  color: #f56c6c;
  text-decoration: line-through;
  border-radius: 3px;
  padding: 2px 4px;
  margin: 0 2px;
}

:deep(.diff-add) {
  background-color: #e6ffec;
  color: #67c23a;
  border-radius: 3px;
  padding: 2px 4px;
  margin: 0 2px;
}

:deep(.diff-equal) {
  color: #2c3e50;
}

// 差异高亮动画 - Git风格
:deep(.diff-highlight) {
  animation: gitDiffHighlight 2s ease-in-out;
}

@keyframes gitDiffHighlight {
  0% {
    background-color: inherit;
    box-shadow: none;
  }
  10% {
    background-color: #fff59d;
    box-shadow: 0 0 8px 2px rgba(255, 235, 59, 0.6);
  }
  80% {
    background-color: #fff59d;
    box-shadow: 0 0 8px 2px rgba(255, 235, 59, 0.6);
  }
  100% {
    background-color: inherit;
    box-shadow: none;
  }
}

@media (max-width: 1400px) {
  .panels {
    height: calc(100vh - 180px);
  }

  .panel {
    min-width: 350px;
  }
}

@media (max-width: 1200px) {
  .panels {
    height: calc(100vh - 160px);
  }

  .panel {
    min-width: 300px;
  }
}

@media (max-width: 1024px) {
  .panels {
    height: calc(100vh - 140px);
  }

  .panel {
    min-width: 250px;
  }

  .panel-content {
    font-size: 14px;
    padding: 20px;
  }
}

@media (max-width: 768px) {
  .panels {
    flex-direction: column;
    height: auto;
    max-height: none;
  }

  .panel {
    min-width: auto;
    max-width: 100%;
    height: calc(50vh - 100px); /* 移动端每侧占屏幕高度的一半 */
    min-height: 300px;
    border-right: none;
    border-bottom: 1px solid #e4e7ed;

    &:last-child {
      border-bottom: none;
    }
  }

  .panel-header {
    .panel-controls {
      gap: 8px;
    }

    .diff-legend {
      display: none; // 移动端隐藏图例
    }

    .nav-buttons {
      .nav-btn {
        width: 28px;
        height: 28px;
        font-size: 14px;
      }

      .diff-counter {
        font-size: 11px;
        min-width: 40px;
      }
    }
  }

  .panel-content {
    font-size: 13px;
    padding: 15px;
    line-height: 1.6;
  }
}

@media (max-width: 480px) {
  .panel {
    height: calc(45vh - 80px); /* 小屏幕进一步调整高度 */
    min-height: 250px;
  }

  .panel-content {
    font-size: 12px;
    padding: 12px;
  }

  .panel-header h3 {
    font-size: 14px;
  }
}
</style>