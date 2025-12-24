<template>
  <div class="file-content-selector">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="15" animated />
      <div class="loading-text">正在解析文件内容...</div>
    </div>

    <!-- 内容区域 - 左右分栏 -->
    <div v-else-if="content" class="content-layout">
      <!-- 左侧：章节列表 -->
      <div class="chapter-sidebar">
        <div class="sidebar-header">
          <h4>文档章节</h4>
          <el-text v-if="availableChapters.length > 0" size="small" type="info">
            {{ availableChapters.length }} 个章节
          </el-text>
        </div>

        <div v-if="availableChapters.length === 0" class="no-chapters">
          <el-empty description="未检测到章节" :image-size="80" />
        </div>

        <div v-else class="chapter-list">
          <div
            v-for="chapter in availableChapters"
            :key="chapter.id"
            :class="['chapter-item', { selected: selectedChapterIds.includes(chapter.id) }]"
            @click="toggleChapter(chapter)"
          >
            <div class="chapter-item-title">{{ chapter.title }}</div>
            <el-checkbox
              :model-value="selectedChapterIds.includes(chapter.id)"
              @change="toggleChapter(chapter)"
            />
          </div>
        </div>

        <!-- 章节操作按钮 -->
        <div v-if="availableChapters.length > 0" class="sidebar-actions">
          <el-button size="small" @click="selectAllChapters">全选</el-button>
          <el-button size="small" @click="clearAllChapters">清除</el-button>
        </div>
      </div>

      <!-- 右侧：文档内容 -->
      <div class="document-area">
        <!-- 已选择信息 -->
        <div v-if="selectedText || selectedChapterIds.length > 0" class="selection-bar">
          <div class="selection-info">
            <el-text type="success">
              <el-icon><Select /></el-icon>
              已选择内容
            </el-text>
            <el-text v-if="selectedText" size="small">
              手动选择：{{ selectedText.length }} 字符
            </el-text>
            <el-text v-if="selectedChapterIds.length > 0" size="small">
              章节选择：{{ selectedChapterIds.length }} 个章节
            </el-text>
          </div>
          <el-button type="text" size="small" @click="clearAll">
            <el-icon><Close /></el-icon>
            清除选择
          </el-button>
        </div>

        <!-- 文档内容显示 -->
        <div
          ref="contentDisplayRef"
          class="content-display"
          @mouseup="handleTextSelection"
          @selectstart="handleSelectionStart"
        >
          <div class="content-text" v-html="highlightedContent"></div>
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Close, Select } from '@element-plus/icons-vue'
import axios from 'axios'

// 章节数据接口
interface Chapter {
  id: string
  title: string
  level: number // 1-6 级标题
  content: string
  preview: string // 内容预览
  startIndex: number // 在全文中的开始位置
  endIndex: number // 在全文中的结束位置
  parentId?: string // 父章节ID
  children?: Chapter[] // 子章节
  hasChildren: boolean // 是否有子章节
}

// 文档结构信息接口
interface DocStructure {
  hasToc: boolean
  headingCount: number
  headings: Array<{ level: number; text: string }>
}

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

// 章节选择相关
const selectionMode = ref<'manual' | 'chapter'>('manual')
const availableChapters = ref<Chapter[]>([])
const selectedChapterIds = ref<string[]>([])
const selectedChapterContent = ref('')
const docStructure = ref<DocStructure | null>(null) // 文档结构信息

// 章节模式相关
const currentChapterId = ref<string | null>(null)
const currentChapter = ref<Chapter | null>(null)
const chapterContentRef = ref<HTMLElement>()
const chapterElements = ref<Map<string, HTMLElement>>(new Map())

// mammoth.js 样式映射配置
const DOCX_STYLE_MAP = [
  "p[style-name='Heading 1'] => h1:fresh",
  "p[style-name='Heading 2'] => h2:fresh",
  "p[style-name='Heading 3'] => h3:fresh",
  "p[style-name='Heading 4'] => h4:fresh",
  "p[style-name='Heading 5'] => h5:fresh",
  "p[style-name='Heading 6'] => h6:fresh",
  // 中英文标题样式
  "p[style-name='标题 1'] => h1:fresh",
  "p[style-name='标题 2'] => h2:fresh",
  "p[style-name='标题 3'] => h3:fresh",
  "p[style-name='标题 4'] => h4:fresh",
  "p[style-name='标题 5'] => h5:fresh",
  "p[style-name='标题 6'] => h6:fresh",
  "p[style-name='标题 1 Char'] => h1:fresh",
  "p[style-name='标题 2 Char'] => h2:fresh",
  "p[style-name='标题 3 Char'] => h3:fresh",
  // 识别可能的目录样式
  "p[style-name='TOC'] => p.toc:fresh",
  "p[style-name='Table of Contents'] => p.toc:fresh",
  "p[style-name='Contents'] => p.toc:fresh",
  "p[style-name='目录'] => p.toc:fresh",
  // 识别其他可能的标题样式
  "p[style-name='标题'] => h2:fresh",
  "p[style-name='章标题'] => h1:fresh",
  "p[style-name='节标题'] => h2:fresh",
  "p[style-name='小标题'] => h3:fresh"
]

// 分析.docx文件结构，检测目录信息
const analyzeDocxStructure = async (arrayBuffer: ArrayBuffer): Promise<{
  hasToc: boolean
  headingCount: number
  headings: Array<{ level: number; text: string }>
}> => {
  try {
    const mammoth = await import('mammoth')

    // 提取原始文档结构信息
    const result = await mammoth.extractRawText({
      arrayBuffer,
      styleMap: DOCX_STYLE_MAP.join('\n')
    })

    // 转换为HTML来分析标题结构
    const htmlResult = await mammoth.convertToHtml({
      arrayBuffer,
      styleMap: DOCX_STYLE_MAP.join('\n')
    })

    const text = result.value
    const html = htmlResult.value

    // 分析标题
    const headings = extractHeadingsFromHtml(html)
    const headingCount = headings.length

    // 检测目录相关的关键词
    const tocKeywords = [
      'table of contents', 'contents', '目录', '目 录', 'index', 'catalog',
      'chapter', 'section', '章节', '第.*章', '第.*节'
    ]

    const hasTocKeywords = tocKeywords.some(keyword =>
      new RegExp(keyword, 'gi').test(text)
    )

    // 检测HTML中是否有目录标记
    const hasTocMarkup = html.includes('<p class="toc">') ||
                        html.includes('class="MsoToc') ||
                        html.includes('toc ')

    // 检测页码模式（目录通常包含页码）
    const hasPageNumbers = /\.{3,}\s*\d+|\s+\d+\s*$/.test(text)

    // 检测文档开头是否有目录结构
    const firstPartText = text.substring(0, Math.min(2000, text.length))
    const hasTocAtStart = (
      hasTocKeywords ||
      hasTocMarkup ||
      (hasPageNumbers && headingCount > 3)
    )

    return {
      hasToc: hasTocAtStart || (headingCount >= 5 && hasPageNumbers),
      headingCount,
      headings: headings.slice(0, 10) // 只返回前10个标题
    }
  } catch (error) {
    console.warn('分析文档结构失败:', error)
    return {
      hasToc: false,
      headingCount: 0,
      headings: []
    }
  }
}

// 从HTML中提取标题信息（只提取H1作为一级标题）
const extractHeadingsFromHtml = (html: string): Array<{ level: number; text: string }> => {
  const headings: Array<{ level: number; text: string }> = []

  // 只查找H1标签作为一级标题
  const h1Regex = /<h1[^>]*>(.*?)<\/h1>/gi
  let match

  while ((match = h1Regex.exec(html)) !== null) {
    if (match[1] !== undefined) {
      const text = match[1].replace(/<[^>]*>/g, '').trim()
      if (text && text.length > 0) {
        headings.push({ level: 1, text })
      }
    }
  }

  // 如果没有H1标签，尝试从纯文本中识别可能的标题
  if (headings.length === 0) {
    const plainText = html.replace(/<[^>]*>/g, '\n').replace(/\n+/g, '\n').trim()
    const lines = plainText.split('\n')

    for (let i = 0; i < Math.min(100, lines.length); i++) {
      const line = lines[i]?.trim()

      if (!line) continue

      if (line.length > 5 && line.length < 200 &&
          // 明显的标题特征
          !line.endsWith('。') &&
          !line.endsWith('.') &&
          !line.match(/^\d{4}年/) &&
          !line.match(/^(图|表|附件|附表)/) &&
          !line.includes('http') &&
          (/[\u4e00-\u9fff]/.test(line) || /[a-zA-Z]/.test(line))) {
        headings.push({ level: 1, text: line })
      }
    }
  }

  return headings
}

// 提取所有 h1 作为一级章节（完全按照您的代码）
const extractChaptersFromDocx = async (
  htmlContent: string
): Promise<Chapter[]> => {
  const chapters: Chapter[] = []

  // 创建临时DOM来解析HTML
  const parser = new DOMParser()
  const doc = parser.parseFromString(htmlContent, 'text/html')

  // 查找所有 h1 元素
  const headings = doc.querySelectorAll('h1')

  if (headings.length === 0) {
    return chapters
  }

  // 为每个h1创建章节（按照您的代码逻辑）
  headings.forEach((heading) => {
    const text = heading.textContent?.trim() || ''
    if (!text) return

    // 生成唯一 id（完全按照您的代码）
    let id = heading.id || slugify(text)
    let counter = 1
    while (doc.getElementById(id)) {
      id = slugify(text + '-' + counter++)
    }
    heading.id = id

    // 收集该h1到下一个h1之间的所有内容
    const allElements = Array.from(doc.body.children)
    const startIndex = Array.from(allElements).indexOf(heading)

    let endIndex = allElements.length
    for (let i = startIndex + 1; i < allElements.length; i++) {
      if (allElements[i]?.tagName === 'H1') {
        endIndex = i
        break
      }
    }

    const sectionElements = Array.from(allElements).slice(startIndex, endIndex)

    // 提取章节内容
    const chapterContent = sectionElements.map(el => {
      return el.textContent?.trim() || ''
    }).filter(text => text.length > 0).join('\n')

    // 生成预览（取前3段文字）
    const preview = sectionElements
      .filter(el => el.tagName === 'P')
      .slice(0, 3)
      .map(el => el.textContent?.trim() || '')
      .join(' ')
      .substring(0, 150)

    const chapter: Chapter = {
      id: id,
      title: text,
      level: 1,
      content: chapterContent,
      preview: preview,
      startIndex: startIndex,
      endIndex: endIndex,
      parentId: undefined,
      hasChildren: false,
      children: []
    }

    chapters.push(chapter)
  })

  return chapters
}

// 简单 slugify（完全按照您的代码）
const slugify = (text: string): string => {
  return text
    .toLowerCase()
    .replace(/[^\w\u4e00-\u9fa5]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .replace(/-+/g, '-')
}

// 计算两个字符串的相似度（使用Levenshtein距离）
const calculateSimilarity = (s1: string, s2: string): number => {
  if (s1.length === 0) return 0
  if (s2.length === 0) return 0

  const longer = s1.length > s2.length ? s1 : s2
  const shorter = s1.length > s2.length ? s2 : s1

  if (longer.length === 0) return 1.0

  const distance = levenshteinDistance(longer, shorter)
  return (longer.length - distance) / longer.length
}

// 计算Levenshtein距离
const levenshteinDistance = (s1: string, s2: string): number => {
  // 处理边界情况
  if (!s1 || !s2) return Math.max(s1?.length || 0, s2?.length || 0)

  const len1 = s1.length
  const len2 = s2.length

  // 使用更简单的实现避免类型问题
  if (len1 === 0) return len2
  if (len2 === 0) return len1

  const prevRow = Array(len1 + 1).fill(0).map((_, i) => i)
  const currentRow = Array(len1 + 1).fill(0)

  for (let i = 1; i <= len2; i++) {
    currentRow[0] = i
    for (let j = 1; j <= len1; j++) {
      const cost = s1[j - 1] === s2[i - 1] ? 0 : 1
      const insertCost = (currentRow[j - 1] ?? 0) + 1
      const deleteCost = (prevRow[j] ?? 0) + 1
      const substituteCost = (prevRow[j - 1] ?? 0) + cost
      currentRow[j] = Math.min(insertCost, deleteCost, substituteCost)
    }
    // 复制当前行到前一行
    for (let j = 0; j <= len1; j++) {
      prevRow[j] = currentRow[j] ?? 0
    }
  }

  return prevRow[len1] ?? 0
}

// 生成章节内容预览
const generateChapterPreview = (content: string): string => {
  // 移除标题行，取内容的开始部分
  const lines = content.split('\n').filter(line => line.trim().length > 0)
  if (lines.length <= 1) return ''

  // 跳过第一行（通常是标题），取后续内容
  const contentLines = lines.slice(1, 4) // 取前3行内容作为预览
  const preview = contentLines.join(' ').trim()

  // 限制预览长度
  return preview.length > 150 ? preview.substring(0, 147) + '...' : preview
}

// 章节选择相关函数
const onModeChange = (mode: 'manual' | 'chapter') => {
  if (mode === 'manual') {
    // 切换到手动模式时，清空章节选择
    selectedChapterIds.value = []
    selectedChapterContent.value = ''
    emit('update:modelValue', selectedText.value)
  } else {
    // 切换到章节模式时，清空手动选择
    selectedText.value = ''
    emit('update:modelValue', selectedChapterContent.value)
  }
}

const onChapterSelectionChange = (chapterIds: string[]) => {
  // 组装选中章节的内容
  const selectedContents = chapterIds
    .map(id => availableChapters.value.find(chapter => chapter.id === id))
    .filter(chapter => chapter)
    .map(chapter => chapter!.content)

  selectedChapterContent.value = selectedContents.join('\n\n---\n\n')
  emit('update:modelValue', selectedChapterContent.value)
  emit('textSelected', selectedChapterContent.value)
}

const selectAllChapters = () => {
  selectedChapterIds.value = availableChapters.value.map(chapter => chapter.id)
  onChapterSelectionChange(selectedChapterIds.value)
}

const clearAllChapters = () => {
  selectedChapterIds.value = []
  selectedChapterContent.value = ''
  currentChapterId.value = null
  currentChapter.value = null
  emit('update:modelValue', '')
  emit('textSelected', '')

  // 清除所有高亮
  nextTick(() => {
    clearHighlights()
  })
}

// 切换章节选择
const toggleChapter = (chapter: Chapter) => {
  const index = selectedChapterIds.value.indexOf(chapter.id)
  if (index > -1) {
    // 已选中，取消选择
    selectedChapterIds.value.splice(index, 1)
  } else {
    // 未选中，添加选择
    selectedChapterIds.value.push(chapter.id)
  }
  onChapterSelectionChange(selectedChapterIds.value)
}

// 清除所有选择（包括手动选择和章节选择）
const clearAll = () => {
  selectedText.value = ''
  selectedChapterIds.value = []
  selectedChapterContent.value = ''
  currentChapterId.value = null
  currentChapter.value = null
  emit('update:modelValue', '')
  emit('textSelected', '')

  // 清除所有高亮
  nextTick(() => {
    clearHighlights()
  })
}

const clearChapterSelection = () => {
  selectedChapterIds.value = []
  selectedChapterContent.value = ''
  emit('update:modelValue', '')
  emit('textSelected', '')
}

const getChapterById = (id: string): Chapter | undefined => {
  return availableChapters.value.find(chapter => chapter.id === id)
}

// 点击目录项（完全按照您的代码）
const selectChapter = (id: string) => {
  const heading = document.getElementById(id)
  if (!heading) return

  heading.scrollIntoView({ behavior: 'smooth', block: 'start' })
  highlightSection(heading)
}

// 高亮当前章节（完全按照您的代码）
const highlightSection = (startHeading: Element) => {
  clearHighlights()
  currentChapterId.value = startHeading.id

  if (!chapterContentRef.value) return

  const allElements = Array.from(chapterContentRef.value.children)
  const startIndex = allElements.indexOf(startHeading)
  if (startIndex === -1) return

  let endIndex = allElements.length
  for (let i = startIndex + 1; i < allElements.length; i++) {
    const element = allElements[i]
    if (element && element.tagName === 'H1') {
      endIndex = i
      break
    }
  }

  const sectionEls = allElements.slice(startIndex, endIndex)
  sectionEls.forEach(el => el.classList.add('section-highlight'))
}

// 清除高亮（完全按照您的代码）
const clearHighlights = () => {
  currentChapterId.value = null
  if (!chapterContentRef.value) return
  chapterContentRef.value.querySelectorAll('.section-highlight').forEach(el => {
    el.classList.remove('section-highlight')
  })
}

// 选择并滚动到指定章节（适配您的代码）
const selectAndScrollToChapter = (chapter: Chapter) => {
  // 使用您的 selectChapter 逻辑
  selectChapter(chapter.id)

  // 自动选择该章节内容
  if (!selectedChapterIds.value.includes(chapter.id)) {
    selectedChapterIds.value = [...selectedChapterIds.value, chapter.id]
    onChapterSelectionChange(selectedChapterIds.value)
  }
}

// 高亮章节内容（适配您的代码）
const highlightChapter = (chapterId: string) => {
  const heading = document.getElementById(chapterId)
  if (heading) {
    highlightSection(heading)
  }
}

// 处理内容区域点击事件（可选：点击空白处取消高亮）
const handleContentClick = (event: Event) => {
  // 如果点击的是背景区域，可以清除高亮
  // if (event.target === chapterContentRef.value) {
  //   currentChapterId.value = null
  //   currentChapter.value = null
  //   clearHighlights()
  // }
}

// 初始化章节元素映射
const initializeChapterElements = () => {
  if (!chapterContentRef.value) return

  // 清空之前的映射
  chapterElements.value.clear()

  // 只查找H1元素
  const headings = chapterContentRef.value.querySelectorAll('h1')

  headings.forEach((heading) => {
    const headingText = heading.textContent?.trim() || ''
    const chapter = availableChapters.value.find(c =>
      c.title.trim() === headingText ||
      headingText.includes(c.title.trim())
    )

    if (chapter) {
      // 为元素添加ID，使其可以通过querySelector找到
      heading.id = `chapter-${chapter.id}`
      chapterElements.value.set(chapter.id, heading as HTMLElement)
    }
  })
}



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
      const response = await axios.get('/contract/file/stream', {
        params: { path: filePath },
        responseType: 'arraybuffer'
      })

      // 使用mammoth.js解析，获取纯文本和HTML两种格式
      const arrayBuffer = response.data
      const mammoth = await import('mammoth')

      // 首先检测文档结构和目录
      docStructure.value = await analyzeDocxStructure(arrayBuffer)
      console.log('文档结构分析结果:', docStructure.value)

      // 获取纯文本版本用于选择
      const textResult = await mammoth.extractRawText({ arrayBuffer })
      rawContent.value = textResult.value

      // 获取HTML版本用于显示
      const htmlResult = await mammoth.convertToHtml({
        arrayBuffer,
        includeDefaultStyleMap: true,
        styleMap: DOCX_STYLE_MAP.join('\n')
      })

      content.value = htmlResult.value

      // 提取章节信息（使用完全基于您示例代码的方法）
      availableChapters.value = await extractChaptersFromDocx(htmlResult.value)

    } else if (isDoc) {
      // 处理.doc文件
      const response = await axios.get('/contract/file/stream', {
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

    //ElMessage.success('文件内容加载完成')
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
    const uint8Array = new Uint8Array(arrayBuffer)
    console.log('开始解析.doc文件，大小:', arrayBuffer.byteLength, 'bytes')

    // 尝试多种方法提取文本
    let extractedText = ''
    const methods: (() => string)[] = [
      () => extractTextUsingMultipleEncodings(uint8Array),
      () => extractTextFromOleStream(uint8Array),
      () => extractTextUsingPatternMatching(uint8Array),
      () => extractReadableText(uint8Array)
    ]

    for (let i = 0; i < methods.length; i++) {
      try {
        const method = methods[i]
        if (method) {
          extractedText = method()
          console.log(`方法 ${i + 1} 提取结果长度:`, extractedText.length)
        }

        if (isReasonableText(extractedText)) {
          console.log(`方法 ${i + 1} 成功提取有效文本`)
          break
        }
      } catch (error) {
        console.warn(`方法 ${i + 1} 失败:`, error)
      }
    }

    // 如果所有方法都失败，返回友好提示
    if (!isReasonableText(extractedText)) {
      return getDocParsingErrorMessage(arrayBuffer.byteLength)
    }

    return cleanExtractedText(extractedText)
  } catch (error) {
    console.error('解析.doc文件失败:', error)
    return getDocParsingErrorMessage(arrayBuffer.byteLength)
  }
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

// 多种编码方法提取文本
const extractTextUsingMultipleEncodings = (uint8Array: Uint8Array): string => {
  const encodings = ['utf-16le', 'utf-16be', 'gb2312', 'gbk', 'big5', 'ascii']

  for (const encoding of encodings) {
    try {
      const decoder = new TextDecoder(encoding, { fatal: false })
      let text = ''

      // 跳过可能的.doc文件头
      let startOffset = 0
      if (uint8Array.length > 1024) {
        startOffset = 1024 // 跳过前1KB的文件头
      }

      // 尝试解码
      const chunkSize = 4096
      for (let i = startOffset; i < uint8Array.length; i += chunkSize) {
        const chunk = uint8Array.slice(i, Math.min(i + chunkSize, uint8Array.length))
        const decoded = decoder.decode(chunk, { stream: i + chunkSize < uint8Array.length })

        // 过滤掉明显的控制字符和乱码
        const filtered = decoded.replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F-\x9F]/g, '')
        if (filtered.trim().length > 0) {
          text += filtered
        }
      }

      if (text.length > 50 && isReasonableText(text)) {
        return text
      }
    } catch (error) {
      continue
    }
  }

  return ''
}

// 从OLE流中提取文本（模拟.doc文件结构）
const extractTextFromOleStream = (uint8Array: Uint8Array): string => {
  let text = ''

  // .doc文件包含多个数据流，尝试查找文本数据流
  // 这里使用启发式方法查找可能的文本区域

  for (let i = 0; i < uint8Array.length - 100; i++) {
    // 查找可能的文本开始位置
    if (isLikelyTextStart(uint8Array, i)) {
      const segment = extractTextSegment(uint8Array, i)
      if (segment.length > 10 && isReadableSegment(segment)) {
        text += segment + '\n'
        i += segment.length // 跳过已处理的区域
      }
    }
  }

  return text
}

// 使用模式匹配提取文本
const extractTextUsingPatternMatching = (uint8Array: Uint8Array): string => {
  let text = ''
  let buffer = ''

  for (let i = 0; i < uint8Array.length; i++) {
    const byte = uint8Array[i]

    // 查找可打印字符
    if (byte !== undefined && isPrintableChar(byte)) {
      buffer += String.fromCharCode(byte)
    } else {
      // 遇到非打印字符时，检查缓冲区
      if (buffer.length > 5 && containsRealText(buffer)) {
        text += buffer + ' '
      }
      buffer = ''
    }
  }

  // 检查最后的缓冲区
  if (buffer.length > 5 && containsRealText(buffer)) {
    text += buffer
  }

  return text
}

// 提取可读文本的简化版本
const extractReadableText = (uint8Array: Uint8Array): string => {
  let text = ''
  const textBlocks: string[] = []
  let currentBlock = ''

  for (let i = 0; i < uint8Array.length; i++) {
    const charCode = uint8Array[i]

    if (charCode !== undefined && charCode >= 32 && charCode <= 126) {
      // ASCII字符
      currentBlock += String.fromCharCode(charCode)
    } else if (charCode !== undefined && charCode >= 128 && charCode <= 255) {
      // 扩展ASCII，可能是中文字符的一部分
      currentBlock += String.fromCharCode(charCode)
    } else if (charCode === 10 || charCode === 13) {
      // 换行符
      if (currentBlock.trim().length > 3) {
        textBlocks.push(currentBlock.trim())
        currentBlock = ''
      }
    } else if (charCode === 0) {
      // 空字符，可能表示文本块的结束
      if (currentBlock.trim().length > 3) {
        textBlocks.push(currentBlock.trim())
        currentBlock = ''
      }
    }
  }

  // 添加最后一个文本块
  if (currentBlock.trim().length > 3) {
    textBlocks.push(currentBlock.trim())
  }

  // 过滤和组合文本块
  return textBlocks
    .filter(block => isReasonableBlock(block))
    .join('\n')
}

// 辅助函数：判断是否可能是文本开始位置
const isLikelyTextStart = (uint8Array: Uint8Array, index: number): boolean => {
  if (index >= uint8Array.length - 10) return false

  // 查找常见的文本开始模式
  const char = uint8Array[index]

  if (char === undefined) {
    return false
  }

  const charValue = char // 确保TypeScript知道这不是undefined

  // 以字母开头
  if ((charValue >= 65 && charValue <= 90) || (charValue >= 97 && charValue <= 122)) {
    return true
  }

  // 以数字开头（可能是序号）
  if (charValue >= 48 && charValue <= 57) {
    return true
  }

  // 中文字符的开始（简体中文）
  if (charValue >= 176 && charValue <= 247) {
    return true
  }

  return false
}

// 提取文本段
const extractTextSegment = (uint8Array: Uint8Array, startIndex: number): string => {
  let segment = ''
  let consecutiveNonPrintable = 0
  const maxNonPrintable = 3

  for (let i = startIndex; i < uint8Array.length && i < startIndex + 500; i++) {
    const byte = uint8Array[i]

    if (byte !== undefined && (isPrintableChar(byte) || byte === 10 || byte === 13 || byte === 9)) {
      segment += String.fromCharCode(byte)
      consecutiveNonPrintable = 0
    } else {
      consecutiveNonPrintable++
      if (consecutiveNonPrintable > maxNonPrintable) {
        break
      }
    }
  }

  return segment
}

// 判断是否为可打印字符
const isPrintableChar = (charCode: number): boolean => {
  // ASCII可打印字符
  if (charCode >= 32 && charCode <= 126) {
    return true
  }

  // 扩展ASCII字符（包含部分中文字符）
  if (charCode >= 128 && charCode <= 255) {
    return true
  }

  return false
}

// 判断是否包含真实文本
const containsRealText = (text: string): boolean => {
  // 检查是否包含字母或中文
  const hasLetters = /[a-zA-Z]/.test(text)
  const hasChinese = /[\u4e00-\u9fff]/.test(text)
  const hasNumbers = /\d/.test(text)

  return (hasLetters || hasChinese || hasNumbers) && text.length >= 3
}

// 判断是否为合理的文本块
const isReasonableBlock = (block: string): boolean => {
  if (block.length < 3) return false

  // 检查文本质量
  const printableRatio = (block.match(/[a-zA-Z0-9\u4e00-\u9fff]/g) || []).length / block.length

  return printableRatio > 0.6 && containsRealText(block)
}

// 判断是否为可读的文本段
const isReadableSegment = (segment: string): boolean => {
  const hasCommonWords = /\b(the|and|or|of|to|in|with|for|on|at|by|from|as|is|was|are|been)\b/i.test(segment)
  const hasChinese = /[\u4e00-\u9fff]/.test(segment)
  const hasNumbers = /\d+/.test(segment)

  return hasCommonWords || hasChinese || hasNumbers
}

// 获取解析错误提示
const getDocParsingErrorMessage = (fileSize: number): string => {
  return `DOC文件解析提示

文件大小: ${(fileSize / 1024).toFixed(2)} KB

由于.doc格式的复杂性，当前无法完整解析此文件内容。

建议解决方案：
1. 使用Microsoft Word将文件另存为.docx格式
2. 使用WPS Office另存为.docx格式
3. 将文件内容复制粘贴到新的文本文件中
4. 使用在线转换工具转换为.docx格式

.docx格式支持完整的内容预览和文本选择功能。`
}

// 清理提取的文本
const cleanExtractedText = (text: string): string => {
  return text
    // 移除过多的控制字符
    .replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F-\x9F]/g, '')
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

// 监听内容变化，初始化章节元素映射
watch(() => [content.value, availableChapters.value], () => {
  if (content.value && availableChapters.value.length > 0) {
    // 延迟执行，确保DOM已更新
    nextTick(() => {
      initializeChapterElements()
    })
  }
}, { immediate: true })

// 监听文件路径变化
watch(() => props.filePath, (newFilePath) => {
  if (newFilePath) {
    loadFileContent(newFilePath)
  } else {
    resetContent()
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

// 重置所有内容
const resetContent = () => {
  content.value = ''
  rawContent.value = ''
  selectedText.value = ''
  selectionMode.value = 'manual'
  availableChapters.value = []
  selectedChapterIds.value = []
  selectedChapterContent.value = ''
  currentChapterId.value = null
  currentChapter.value = null
  chapterElements.value.clear()
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
  height: 100%;
  display: flex;
  flex-direction: column;
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

/* 内容布局 - 左右分栏 */
.content-layout {
  display: flex;
  height: 100%;
  gap: 16px;
  overflow: hidden;
}

/* 左侧章节列表 */
.chapter-sidebar {
  width: 240px;
  display: flex;
  flex-direction: column;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
  flex-shrink: 0;
}

.sidebar-header {
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  background: #f8f9fa;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sidebar-header h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #2c3e50;
}

.no-chapters {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.chapter-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.chapter-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 4px;
}

.chapter-item:hover {
  background: #f5f7fa;
}

.chapter-item.selected {
  background: #e3f2fd;
  border: 1px solid #417FF2;
}

.chapter-item-title {
  flex: 1;
  font-size: 14px;
  color: #2c3e50;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-right: 8px;
}

.sidebar-actions {
  padding: 12px 16px;
  border-top: 1px solid #e4e7ed;
  display: flex;
  gap: 8px;
  justify-content: center;
}

/* 右侧文档区域 */
.document-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.selection-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #f0f9ff;
  border: 1px solid #bae7ff;
  border-radius: 8px;
  margin-bottom: 12px;
}

.selection-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.content-display {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  cursor: text;
  user-select: text;
  line-height: 1.8;
  font-size: 14px;
  background: white;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}

.content-text {
  color: #2c3e50;
}

.content-text :deep(h1),
.content-text :deep(h2),
.content-text :deep(h3),
.content-text :deep(h4),
.content-text :deep(h5),
.content-text :deep(h6) {
  margin-top: 20px;
  margin-bottom: 10px;
  font-weight: 600;
  color: #2c3e50;
}

.content-text :deep(h1) {
  font-size: 24px;
  border-bottom: 2px solid #e4e7ed;
  padding-bottom: 8px;
}

.content-text :deep(h2) {
  font-size: 20px;
}

.content-text :deep(h3) {
  font-size: 18px;
}

.content-text :deep(p) {
  margin-bottom: 12px;
  text-align: justify;
}

/* 高亮样式 */
.content-text :deep(.highlight-chapter) {
  background-color: #fff59d;
  padding: 2px 4px;
  border-radius: 2px;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}
</style>
