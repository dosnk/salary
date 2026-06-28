<template>
  <van-popup v-model:show="visible" position="center" :style="{ width: '98%', maxWidth: '900px', maxHeight: '90vh' }">
    <div class="attachments-popup">
      <div class="attachments-header">
        <div class="header-left">
          <span class="attachments-title">{{ projectName }} ({{ files.length }})</span>
        </div>
        <div class="header-right">
          <van-icon name="cross" @click="closePopup" />
        </div>
      </div>
      
      <div class="attachments-body">
        <div v-if="files.length === 0" class="empty-attachments">
          <van-empty description="暂无附件" />
        </div>
        
        <div v-else class="attachments-list single-column">
          <div 
            v-for="file in files" 
            :key="file.id" 
            :class="['attachment-item', { 'is-image': isImage(file.type), 'is-video': isVideo(file.type) }]"
          >
            <div class="attachment-preview" :ref="el => setItemRef(el, file.id)" @click="handlePreview(file)">
              <template v-if="isImage(file.type)">
                <img 
                  v-if="imageSrcMap[file.id]" 
                  :src="imageSrcMap[file.id]" 
                  :alt="file.originalName || file.filename" 
                  class="attachment-image" 
                  @load="onImageLoad(file.id)" 
                  @error="onImageError(file.id)"
                  loading="lazy"
                />
                <div v-if="loadingStates[file.id] !== 'loaded'" class="image-loading">
                  <van-loading size="24" />
                </div>
                <div v-if="loadingStates[file.id] === 'error'" class="image-error" @click.stop="retryImage(file)">
                  <van-icon name="replay" size="32" />
                  <span>点击重试</span>
                </div>
              </template>
              <video 
                v-else-if="isVideo(file.type)" 
                :src="file.url" 
                controls 
                class="attachment-video" 
                preload="metadata"
                @click.stop
              />
              <div v-else class="attachment-icon-wrapper">
                <van-icon :name="getFileIcon(file.type)" size="48" class="attachment-icon" />
                <span class="file-extension">{{ getFileExtension(file) }}</span>
              </div>
            </div>
            
            <div class="attachment-info">
              <div class="attachment-actions">
                <van-button size="small" type="primary" icon="down" @click.stop="handleDownload(file)">下载 ({{ formatFileSize(file.size) }})</van-button>
                <van-button v-if="canDelete" size="small" type="danger" icon="delete" @click.stop="handleDelete(file)">删除</van-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </van-popup>

  <van-popup v-model:show="showPreview" position="center" :style="{ width: '100%', height: '100%', background: 'transparent' }">
    <div class="preview-popup-full">
      <div class="preview-header">
        <div class="preview-header-actions">
          <van-icon name="cross" size="28" @click="closePreview" />
        </div>
      </div>
      
      <div class="preview-content" @wheel.passive="handleWheel" @mousedown="handleMouseDown" @mousemove="handleMouseMove" @mouseup="handleMouseUp" @mouseleave="handleMouseUp" @touchstart.passive="handleTouchStart" @touchmove="handleTouchMove" @touchend="handleTouchEnd" @touchcancel="handleTouchEnd">
        <div class="preview-image-container" :style="{ transform: `translate(${translateX}px, ${translateY}px) scale(${scale})` }">
          <img 
            v-if="currentPreviewFile && isImage(currentPreviewFile.type)" 
            :src="previewImageSrc" 
            :alt="currentPreviewFile.originalName || currentPreviewFile.filename" 
            class="preview-image" 
            @load="onPreviewLoad" 
            @error="onPreviewError"
          />
          <div v-if="previewLoading" class="preview-loading">
            <van-loading size="32" />
          </div>
          <div v-if="previewError" class="preview-error" @click="retryPreview">
            <van-icon name="replay" size="48" />
            <span>加载失败，点击重试</span>
          </div>
          <video 
            v-else-if="currentPreviewFile && isVideo(currentPreviewFile.type)" 
            :src="currentPreviewFile.url" 
            controls 
            class="preview-video"
            autoplay
          />
          <div v-else-if="currentPreviewFile && !isImage(currentPreviewFile.type) && !isVideo(currentPreviewFile.type)" class="preview-unsupported">
            <van-icon :name="getFileIcon(currentPreviewFile.type)" size="80" />
            <p class="file-name">{{ currentPreviewFile.originalName || currentPreviewFile.filename }}</p>
            <p>此文件类型不支持预览，请下载后查看</p>
            <van-button type="primary" size="large" icon="down" @click="handleDownload(currentPreviewFile)">下载文件</van-button>
          </div>
        </div>
      </div>
      
      <div class="preview-footer">
        <van-button type="default" size="small" icon="arrow-left" @click="handlePrev">上一张</van-button>
        <div class="preview-counter">{{ currentIndex + 1 }} / {{ files.length }}</div>
        <van-button type="default" size="small" @click="handleNext">下一张 <van-icon name="arrow" /></van-button>
      </div>
    </div>
  </van-popup>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { showConfirmDialog, showToast } from 'vant'

interface FileItem {
  id: number
  originalName?: string
  original_name?: string
  filename: string
  url: string
  path?: string
  size: number
  type: string
}

const props = defineProps<{
  show: boolean
  projectName: string
  files: FileItem[]
  canDelete?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:show', value: boolean): void
  (e: 'delete', file: FileItem): void
}>()


const visible = computed({
  get: () => props.show,
  set: (value) => emit('update:show', value)
})

const showPreview = ref(false)
const currentPreviewFile = ref<FileItem | null>(null)
const currentIndex = ref(0)
const scale = ref(1)
const translateX = ref(0)
const translateY = ref(0)
const lastMouseX = ref(0)
const lastMouseY = ref(0)
const isDragging = ref(false)
const lastTouchX = ref(0)
const lastTouchY = ref(0)
const lastTouchDistance = ref(0)
const loadingStates = ref<Record<number, 'loading' | 'loaded' | 'error'>>({})
const imageSrcMap = ref<Record<number, string>>({})
const previewLoading = ref(false)
const previewError = ref(false)
const previewImageSrc = computed(() => {
  if (!currentPreviewFile.value) return ''
  const cachedSrc = imageSrcMap.value[currentPreviewFile.value.id]
  return cachedSrc || currentPreviewFile.value.url
})
const itemRefs = new Map<number, Element>()
const loadQueue = ref<number[]>([])
const MAX_CONCURRENT_LOADS = 3
let currentLoadingCount = 0
let observer: IntersectionObserver | null = null

const setItemRef = (el: any, fileId: number) => {
  if (el) {
    itemRefs.set(fileId, el)
  } else {
    itemRefs.delete(fileId)
  }
}

const initLoadingStates = () => {
  const states: Record<number, 'loading'> = {}
  const srcMap: Record<number, string> = {}
  const queue: number[] = []
  
  props.files.forEach(file => {
    if (isImage(file.type)) {
      states[file.id] = 'loading'
      queue.push(file.id)
    }
  })
  
  loadingStates.value = states
  imageSrcMap.value = srcMap
  loadQueue.value = queue
}

const startSequentialLoad = () => {
  if (observer) {
    observer.disconnect()
  }
  
  observer = new IntersectionObserver(
    (entries) => {
      entries.forEach(entry => {
        const fileId = Number(entry.target.getAttribute('data-file-id'))
        if (entry.isIntersecting && loadingStates.value[fileId] === 'loading' && !imageSrcMap.value[fileId]) {
          loadImage(fileId)
        }
      })
    },
    {
      root: null,
      rootMargin: '200px',
      threshold: 0.1
    }
  )
  
  itemRefs.forEach((el, fileId) => {
    el.setAttribute('data-file-id', String(fileId))
    observer!.observe(el)
  })
  
  const visibleItems = getVisibleItems()
  visibleItems.slice(0, MAX_CONCURRENT_LOADS).forEach(fileId => {
    if (loadingStates.value[fileId] === 'loading' && !imageSrcMap.value[fileId]) {
      loadImage(fileId)
    }
  })
}

const getVisibleItems = (): number[] => {
  const visible: number[] = []
  itemRefs.forEach((el, fileId) => {
    const rect = el.getBoundingClientRect()
    const viewportHeight = window.innerHeight
    if (rect.top < viewportHeight && rect.bottom > 0) {
      visible.push(fileId)
    }
  })
  return visible.sort((a, b) => {
    const rectA = itemRefs.get(a)?.getBoundingClientRect()
    const rectB = itemRefs.get(b)?.getBoundingClientRect()
    return (rectA?.top || 0) - (rectB?.top || 0)
  })
}

const loadImage = (fileId: number) => {
  const file = props.files.find(f => f.id === fileId)
  if (!file || imageSrcMap.value[fileId]) return
  
  currentLoadingCount++
  imageSrcMap.value[fileId] = file.url
  
  // 创建 Image 对象预加载，确保状态正确更新
  const img = new Image()
  img.onload = () => {
    onImageLoad(fileId)
  }
  img.onerror = () => {
    onImageError(fileId)
  }
  img.src = file.url
}

const onImageLoad = (fileId: number) => {
  loadingStates.value[fileId] = 'loaded'
  currentLoadingCount = Math.max(0, currentLoadingCount - 1)
  
  if (currentLoadingCount < MAX_CONCURRENT_LOADS) {
    const nextFileId = loadQueue.value.find(id => 
      loadingStates.value[id] === 'loading' && !imageSrcMap.value[id]
    )
    if (nextFileId) {
      loadImage(nextFileId)
    }
  }
}

const onImageError = (fileId: number) => {
  loadingStates.value[fileId] = 'error'
  currentLoadingCount = Math.max(0, currentLoadingCount - 1)
}

const retryImage = (file: FileItem) => {
  loadingStates.value[file.id] = 'loading'
  imageSrcMap.value[file.id] = file.url + '?t=' + Date.now()
}

const handlePreview = (file: FileItem) => {
  currentIndex.value = props.files.findIndex(f => f.id === file.id)
  currentPreviewFile.value = file
  scale.value = 1
  translateX.value = 0
  translateY.value = 0
  previewLoading.value = !!isImage(file.type) && loadingStates.value[file.id] !== 'loaded'
  previewError.value = false
  showPreview.value = true
}

const handlePrev = () => {
  if (currentIndex.value > 0) {
    currentIndex.value--
    currentPreviewFile.value = props.files[currentIndex.value]
    scale.value = 1
    translateX.value = 0
    translateY.value = 0
  }
}

const handleNext = () => {
  if (currentIndex.value < props.files.length - 1) {
    currentIndex.value++
    currentPreviewFile.value = props.files[currentIndex.value]
    scale.value = 1
    translateX.value = 0
    translateY.value = 0
  }
}

const handleDownload = (file: FileItem) => {
  const link = document.createElement('a')
  link.href = file.url
  link.download = file.originalName || file.original_name || file.filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  showToast({ type: 'success', message: '开始下载' })
}

const handleDelete = async (file: FileItem) => {
  try {
    await showConfirmDialog({
      title: '确认删除',
      message: `确定要删除文件 "${file.originalName || file.filename}" 吗？`,
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonColor: '#ef4444'
    })
    emit('delete', file)
  } catch {
  }
}

const closePopup = () => {
  visible.value = false
}

const closePreview = () => {
  showPreview.value = false
  previewLoading.value = false
  previewError.value = false
  currentPreviewFile.value = null
}

const resetPreview = () => {
  scale.value = 1
  translateX.value = 0
  translateY.value = 0
}

const onPreviewLoad = () => {
  previewLoading.value = false
  previewError.value = false
}

const onPreviewError = () => {
  previewLoading.value = false
  previewError.value = true
}

const retryPreview = () => {
  if (!currentPreviewFile.value) return
  previewLoading.value = true
  previewError.value = false
  const img = new Image()
  img.src = currentPreviewFile.value.url + '?t=' + Date.now()
  img.onload = () => {
    previewLoading.value = false
    if (currentPreviewFile.value) {
      currentPreviewFile.value.url = img.src
    }
  }
  img.onerror = () => {
    previewLoading.value = false
    previewError.value = true
  }
}

const handleWheel = (e: WheelEvent) => {
  const delta = e.deltaY > 0 ? -0.1 : 0.1
  scale.value = Math.min(Math.max(0.5, scale.value + delta), 5)
}

const handleMouseDown = (e: MouseEvent) => {
  isDragging.value = true
  lastMouseX.value = e.clientX
  lastMouseY.value = e.clientY
}

const handleMouseMove = (e: MouseEvent) => {
  if (!isDragging.value) return
  const dx = e.clientX - lastMouseX.value
  const dy = e.clientY - lastMouseY.value
  translateX.value += dx
  translateY.value += dy
  lastMouseX.value = e.clientX
  lastMouseY.value = e.clientY
}

const handleMouseUp = () => {
  isDragging.value = false
}

const handleTouchStart = (e: TouchEvent) => {
  if (e.touches.length === 1) {
    lastTouchX.value = e.touches[0].clientX
    lastTouchY.value = e.touches[0].clientY
  } else if (e.touches.length === 2) {
    lastTouchDistance.value = Math.hypot(
      e.touches[0].clientX - e.touches[1].clientX,
      e.touches[0].clientY - e.touches[1].clientY
    )
  }
}

const handleTouchMove = (e: TouchEvent) => {
  e.preventDefault()
  if (e.touches.length === 1) {
    const dx = e.touches[0].clientX - lastTouchX.value
    const dy = e.touches[0].clientY - lastTouchY.value
    translateX.value += dx
    translateY.value += dy
    lastTouchX.value = e.touches[0].clientX
    lastTouchY.value = e.touches[0].clientY
  } else if (e.touches.length === 2) {
    const distance = Math.hypot(
      e.touches[0].clientX - e.touches[1].clientX,
      e.touches[0].clientY - e.touches[1].clientY
    )
    const delta = (distance - lastTouchDistance.value) * 0.01
    scale.value = Math.min(Math.max(0.5, scale.value + delta), 5)
    lastTouchDistance.value = distance
  }
}

const handleTouchEnd = () => {
  lastTouchDistance.value = 0
}

const isImage = (type: string) => {
  return type && type.startsWith('image/')
}

const isVideo = (type: string) => {
  return type && type.startsWith('video/')
}

const formatFileSize = (bytes: number) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i]
}



const getFileIcon = (type: string) => {
  if (!type) return 'description'
  if (type.startsWith('image/')) return 'photo-o'
  if (type.startsWith('video/')) return 'video-o'
  if (type.startsWith('audio/')) return 'music-o'
  if (type.includes('pdf')) return 'description'
  if (type.includes('word') || type.includes('document')) return 'description'
  if (type.includes('excel') || type.includes('sheet')) return 'notes-o'
  return 'description'
}

const getFileExtension = (file: FileItem) => {
  const name = file.originalName || file.filename
  const ext = name.split('.').pop()
  return ext ? ext.toUpperCase() : ''
}

watch(() => props.files, () => {
  initLoadingStates()
}, { immediate: true })

watch(() => props.show, (newVal) => {
  if (newVal) {
    initLoadingStates()
    setTimeout(() => {
      startSequentialLoad()
    }, 100)
  } else {
    showPreview.value = false
    currentPreviewFile.value = null
    if (observer) {
      observer.disconnect()
    }
  }
})

watch(() => showPreview.value, (newVal) => {
  if (newVal) {
    document.body.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = ''
    resetPreview()
  }
})
</script>

<style scoped>
.attachments-popup {
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  max-height: 90vh;
}

.attachments-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
  background: linear-gradient(180deg, #f9fef5 0%, #fff 100%);
}

.header-left {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 12px;
}

.attachments-title {
  font-size: 18px;
  font-weight: 700;
  color: #333;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-right .van-icon {
  font-size: 28px;
  color: #999;
  cursor: pointer;
  transition: color 0.2s;
}

.header-right .van-icon:hover {
  color: #10b981;
}

.attachments-body {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  background: #fafafa;
}

.empty-attachments {
  padding: 60px 0;
}

.attachments-list {
  padding: 16px;
}

.attachments-list.single-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.attachments-list.single-column .attachment-item {
  display: flex !important;
  flex-direction: column !important;
  width: 100% !important;
}

.attachments-list.single-column .attachment-preview {
  width: 100% !important;
  height: auto;
  min-height: 200px;
  max-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  overflow: hidden;
  position: relative;
}

.attachments-list.single-column .attachment-image {
  width: 100% !important;
  height: auto;
  max-height: 400px;
  object-fit: contain;
  transition: transform 0.3s ease;
}

.attachments-list.single-column .attachment-video {
  width: 100% !important;
  height: auto;
  max-height: 400px;
  object-fit: contain;
}

.attachment-item {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: all 0.2s ease;
  cursor: pointer;
}

.attachment-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}



.image-loading {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
}

.image-error {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #fef2f2;
  color: #ef4444;
  cursor: pointer;
  gap: 8px;
}

.image-error span {
  font-size: 12px;
}

.attachment-icon-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  height: 100%;
}

.attachment-icon {
  color: #10b981;
}

.file-extension {
  font-size: 12px;
  font-weight: 600;
  color: #666;
  background: #e6f7e6;
  padding: 2px 8px;
  border-radius: 4px;
}

.attachment-info {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.attachment-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #999;
}

.attachment-actions {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

.attachment-actions .van-button {
  flex: 1;
}

.preview-popup-full {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: transparent;
}

.preview-header {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 16px 24px;
  background: rgba(0, 0, 0, 0.8);
  color: #fff;
  flex-shrink: 0;
  width: 100%;
  box-sizing: border-box;
}



.preview-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.preview-header-actions .van-icon {
  cursor: pointer;
  transition: opacity 0.2s;
}

.preview-header-actions .van-icon:hover {
  opacity: 0.8;
}

.preview-content {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  cursor: grab;
  user-select: none;
  width: 100%;
  position: relative;
  background: transparent;
}

.preview-content:active {
  cursor: grabbing;
}

.preview-image-container {
  transition: transform 0.1s ease-out;
  width: 100%;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-image {
  max-width: 90vw;
  max-height: 70vh;
  width: auto;
  height: auto;
  object-fit: contain;
}

.preview-loading {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #fff;
}

.preview-error {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #fff;
  cursor: pointer;
  gap: 16px;
}

.preview-error span {
  font-size: 16px;
}

.preview-video {
  max-width: 90vw;
  max-height: 70vh;
  width: auto;
  height: auto;
}

.preview-unsupported {
  text-align: center;
  color: #fff;
  padding: 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.preview-unsupported .file-name {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 8px;
}

.preview-unsupported p {
  font-size: 14px;
  opacity: 0.8;
}

.preview-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: rgba(0, 0, 0, 0.8);
  color: #fff;
  flex-shrink: 0;
  width: 100%;
  box-sizing: border-box;
}

.preview-counter {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.8);
}
</style>
