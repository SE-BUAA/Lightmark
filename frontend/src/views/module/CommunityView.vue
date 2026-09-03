<template>
  <div class="module-page">
    <section class="module-hero module-hero--gold">
      <div class="container hero-inner">
        <div>
          <span class="module-kicker">旅行社区</span>
          <h1 class="section-title">分享路线、记录风景、互相回答攻略</h1>
          <p class="section-subtitle">发布游记、参与评论，也可以在问答区向其他旅行者寻求建议。</p>
        </div>
        <el-button type="primary" size="large" @click="openCreatePost">发布游记</el-button>
      </div>
    </section>

    <section class="community-section">
      <div class="container">
        <el-tabs v-model="activeTab" class="community-tabs">
          <el-tab-pane label="游记" name="posts">
            <div class="toolbar">
              <el-input v-model="postKeyword" placeholder="搜索游记" clearable @keyup.enter="loadPosts" />
              <el-select v-model="postSort" @change="loadPosts">
                <el-option label="最新" value="latest" />
                <el-option label="热门" value="hot" />
              </el-select>
              <el-button @click="loadPosts">搜索</el-button>
            </div>

            <el-empty v-if="posts.length === 0" description="暂无游记" />
            <div v-else class="post-grid">
              <article v-for="post in posts" :key="post.id" class="post-card" @click="openPostReader(post)">
                <div class="post-cover">
                  <img v-if="firstImage(post.images)" :src="firstImage(post.images)" :alt="post.title" />
                  <div v-else class="empty-cover">Lightmark</div>
                  <span v-if="imageList(post.images).length > 1" class="photo-count">{{ imageList(post.images).length }}图</span>
                </div>
                <div class="post-body">
                  <h2>{{ post.title }}</h2>
                  <p>{{ plainText(post.content) }}</p>
                  <div class="post-author-line">
                    <span class="mini-avatar">{{ avatarLetter(post.user_nickname, post.user_id) }}</span>
                    <span>{{ displayNickname(post.user_nickname, post.user_id) }}</span>
                    <span class="dot">·</span>
                    <span>{{ formatTime(post.create_time) }}</span>
                  </div>
                  <div class="post-actions" @click.stop>
                    <el-button text :type="post.liked ? 'success' : 'primary'" :class="{ 'liked-action': post.liked }" @click="handleLike(post)">
                      {{ post.liked ? '已赞' : '点赞' }} {{ post.likes }}
                    </el-button>
                    <el-button text @click="openPostReader(post)">评论 {{ post.comments_count }}</el-button>
                    <el-button v-if="canEditPost(post)" text @click="openEditPost(post)">编辑</el-button>
                    <el-button v-if="canDeletePost(post)" text type="danger" @click="handleDeletePost(post)">删除</el-button>
                  </div>
                </div>
              </article>
            </div>
          </el-tab-pane>

          <el-tab-pane label="问答" name="questions">
            <div class="toolbar question-toolbar">
              <el-input v-model="questionKeyword" placeholder="搜索问题" clearable @keyup.enter="loadQuestions" />
              <el-button @click="loadQuestions">搜索</el-button>
              <el-button type="primary" @click="showQuestionDialog = true">提问</el-button>
            </div>

            <el-empty v-if="questions.length === 0" description="暂无问题" />
            <div v-else class="question-list">
              <article v-for="question in questions" :key="question.id" class="question-card">
                <div>
                  <h2>{{ question.title }}</h2>
                  <p>{{ question.content }}</p>
                  <div class="question-meta">
                    <span>提问者：{{ displayNickname(question.user_nickname, question.user_id) }}</span>
                    <span>{{ formatTime(question.create_time) }}</span>
                  </div>
                  <div v-if="question.answer" class="answer-box">
                    <div class="answer-meta">回答者：{{ displayNickname(question.answer_user_nickname, question.answer_user_id) }}</div>
                    {{ question.answer }}
                  </div>
                </div>
                <div class="question-side">
                  <el-tag :type="question.status === 1 ? 'success' : 'info'">{{ question.status === 1 ? '已回答' : '待回答' }}</el-tag>
                  <el-button v-if="canAnswerQuestion(question)" size="small" @click="openAnswer(question)">{{ question.answer ? '编辑回答' : '回答' }}</el-button>
                  <el-button v-if="canDeleteAnswer(question)" size="small" type="warning" plain @click="handleDeleteAnswer(question)">删除回答</el-button>
                  <el-button v-if="canDeleteQuestion(question)" size="small" type="danger" plain @click="handleDeleteQuestion(question)">删除问题</el-button>
                </div>
              </article>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </section>

    <button class="floating-publish" type="button" @click="openCreatePost">+</button>

    <el-dialog v-model="showPostDialog" :show-close="false" width="520px" class="trip-publish-dialog">
      <div class="trip-publish">
        <header class="publish-topbar">
          <button class="icon-button" type="button" @click="showPostDialog = false">‹</button>
          <strong>{{ editingPost?.id ? '编辑游记' : '发布游记' }}</strong>
          <button class="plain-button" type="button" @click="saveDraft">草稿</button>
        </header>

        <div class="quality-tip">笔记质量越好，越容易被更多旅行者看见</div>

        <div class="cover-strip">
          <div v-for="(image, index) in postImages" :key="image" class="cover-tile">
            <img :src="communityImageUrl(image)" alt="游记图片预览" />
            <span v-if="index === 0" class="cover-label">封面</span>
            <button class="remove-image" type="button" @click="removePostImage(image)">×</button>
          </div>
          <button class="upload-tile" type="button" :disabled="uploadingImages" @click="imageInputRef?.click()">
            <span class="camera-mark">＋</span>
            <span>{{ uploadingImages ? '上传中' : '上传图片' }}</span>
          </button>
          <input ref="imageInputRef" class="native-file-input" type="file" accept="image/*" multiple @change="handleImageFiles" />
        </div>

        <input v-model="postForm.title" class="trip-title-input" maxlength="60" placeholder="填写标题更容易上精选" />
        <textarea
          v-model="postForm.content"
          class="trip-content-input"
          rows="10"
          placeholder="写下当天路线、景点体验、餐厅推荐、避坑提醒和照片背后的故事..."
        />

        <div class="assistant-row">
          <button type="button" @click="insertTopic"># 话题</button>
          <button type="button" @click="editorTab = editorTab === 'preview' ? 'edit' : 'preview'">
            {{ editorTab === 'preview' ? '继续编辑' : '效果预览' }}
          </button>
        </div>

        <article v-if="editorTab === 'preview'" class="markdown-preview publish-preview" v-html="renderMarkdown(postForm.content)"></article>

        <button class="publish-row" type="button" @click="showLocationDialog = true">
          <span class="row-icon">●</span>
          <strong>添加地点或线路</strong>
          <span>{{ postLocation || '越准确越容易被推荐' }}</span>
        </button>

        <button class="publish-row" type="button" @click="showAdvancedDialog = true">
          <span class="row-icon">⚙</span>
          <strong>高级选项</strong>
          <span>{{ visibilityText }} · {{ allowComments ? '允许评论' : '关闭评论' }}</span>
        </button>

        <label class="publish-agreement">
          <input v-model="publishAgreed" type="checkbox" />
          <span>同意《拾光社区发布规则》</span>
        </label>

        <div class="publish-footer">
          <button class="draft-button" type="button" @click="saveDraft">存草稿</button>
          <button class="publish-button" type="button" :disabled="posting || !publishAgreed" @click="handleSubmitPost">
            {{ posting ? '发布中' : editingPost?.id ? '保存' : '发布' }}
          </button>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="showLocationDialog" title="添加地点或线路" width="460px">
      <el-form label-position="top">
        <el-form-item label="旅行目的地">
          <el-input v-model="locationForm.destination" maxlength="30" placeholder="例如：杭州西湖、祁连山、成都宽窄巷子" />
        </el-form-item>
        <el-form-item label="线路描述">
          <el-input v-model="locationForm.route" type="textarea" :rows="3" maxlength="80" placeholder="例如：西湖-河坊街-南宋御街" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showLocationDialog = false">取消</el-button>
        <el-button type="primary" @click="handleLocationSubmit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showAdvancedDialog" title="高级选项" width="460px">
      <div class="advanced-options">
        <div class="advanced-option">
          <div>
            <strong>可见范围</strong>
            <p>{{ postVisibility === 'public' ? '发布后所有用户都可以看到' : '仅自己可见，不出现在公开游记列表中' }}</p>
          </div>
          <el-switch v-model="postVisibility" active-value="public" inactive-value="private" active-text="公开" inactive-text="私密" />
        </div>
        <div class="advanced-option">
          <div>
            <strong>评论设置</strong>
            <p>{{ allowComments ? '其他用户可以在详情页评论' : '详情页隐藏评论发布入口' }}</p>
          </div>
          <el-switch v-model="allowComments" />
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="showAdvancedDialog = false">完成</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="showPostReader" size="760px" class="trip-reader-drawer" :with-header="false">
      <article v-if="postDetail" class="trip-reader">
        <header class="reader-topbar">
          <button class="icon-button" type="button" @click="showPostReader = false">‹</button>
          <div class="reader-author">
            <span class="reader-avatar">{{ avatarLetter(postDetail.user_nickname, postDetail.user_id) }}</span>
            <strong>{{ displayNickname(postDetail.user_nickname, postDetail.user_id) }}</strong>
          </div>
          <button class="follow-button" type="button">+ 关注</button>
        </header>

        <el-carousel v-if="imageList(postDetail.images).length" class="reader-carousel" indicator-position="outside" height="460px">
          <el-carousel-item v-for="image in imageList(postDetail.images)" :key="image">
            <img :src="communityImageUrl(image)" :alt="postDetail.title" />
          </el-carousel-item>
        </el-carousel>

        <div class="reader-location-row">
          <span v-for="pill in locationPills(postDetail)" :key="pill" class="location-pill">● {{ pill }}</span>
        </div>

        <h1 class="reader-title">{{ postDetail.title }}</h1>
        <div class="reader-time">{{ formatTime(postDetail.create_time) }} 发布</div>
        <div class="markdown-preview reader-content" v-html="renderMarkdown(postDetail.content, true)"></div>

        <div v-if="readerTags(postDetail).length" class="reader-tags">
          <span v-for="tag in readerTags(postDetail)" :key="tag">#{{ tag }}</span>
        </div>

        <div class="reader-owner-actions">
          <el-button v-if="canEditPost(postDetail)" @click="openEditPost(postDetail)">编辑</el-button>
          <el-button v-if="canDeletePost(postDetail)" type="danger" plain @click="handleDeletePost(postDetail)">删除</el-button>
        </div>

        <section v-if="allowCommentsForPost(postDetail)" class="reader-comments">
          <h3>评论</h3>
          <el-empty v-if="comments.length === 0" description="暂无评论" />
          <div v-for="comment in comments" :key="comment.id" class="comment-item">
            <div class="comment-heading">
              <strong>{{ displayNickname(comment.user_nickname, comment.user_id) }}</strong>
              <el-button v-if="canDeleteComment(comment)" text type="danger" @click="handleDeleteComment(comment)">删除</el-button>
            </div>
            <p>{{ comment.content }}</p>
          </div>
        </section>

        <footer class="reader-bottom-bar">
          <div class="comment-send-row">
            <el-input
              v-model="commentContent"
              :disabled="!allowCommentsForPost(postDetail)"
              placeholder="写评论，天天..."
              @keyup.enter="handleCreateComment"
            />
            <button
              class="send-comment-button"
              type="button"
              :disabled="commenting || !allowCommentsForPost(postDetail) || !commentContent.trim()"
              @click="handleCreateComment"
            >
              发送
            </button>
          </div>
          <div class="reader-action-row">
            <button type="button" :class="{ active: postDetail.liked }" @click="handleLike(postDetail)">赞 {{ postDetail.likes }}</button>
            <button type="button" :disabled="!allowCommentsForPost(postDetail)">评 {{ postDetail.comments_count }}</button>
            <button type="button" @click="handleSharePost(postDetail)">分享</button>
          </div>
        </footer>
      </article>
    </el-drawer>

    <el-dialog v-model="showQuestionDialog" title="发布问题" width="560px">
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="questionForm.title" />
        </el-form-item>
        <el-form-item label="问题描述">
          <el-input v-model="questionForm.content" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showQuestionDialog = false">取消</el-button>
        <el-button type="primary" :loading="questioning" @click="handleCreateQuestion">发布</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showAnswerDialog" title="回答问题" width="560px">
      <p class="answer-title">{{ selectedQuestion?.title }}</p>
      <el-input v-model="answerContent" type="textarea" :rows="5" placeholder="输入回答内容" />
      <template #footer>
        <el-button @click="showAnswerDialog = false">取消</el-button>
        <el-button type="primary" :loading="answering" @click="handleAnswerQuestion">提交回答</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  answerQuestion,
  createPost,
  createPostComment,
  createQuestion,
  deletePost,
  deletePostComment,
  deleteQuestion,
  deleteQuestionAnswer,
  getPost,
  getPostComments,
  getPosts,
  getQuestions,
  togglePostLike,
  updatePost,
  uploadCommunityImage,
  type Comment,
  type Post,
  type Question
} from '@/api/community'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const activeTab = ref('posts')
const posts = ref<Post[]>([])
const questions = ref<Question[]>([])
const comments = ref<Comment[]>([])
const postKeyword = ref('')
const postSort = ref('latest')
const questionKeyword = ref('')
const showPostDialog = ref(false)
const showPostReader = ref(false)
const showLocationDialog = ref(false)
const showAdvancedDialog = ref(false)
const showQuestionDialog = ref(false)
const showAnswerDialog = ref(false)
const selectedPost = ref<Post | null>(null)
const editingPost = ref<Post | null>(null)
const postDetail = ref<Post | null>(null)
const selectedQuestion = ref<Question | null>(null)
const commentContent = ref('')
const answerContent = ref('')
const posting = ref(false)
const commenting = ref(false)
const questioning = ref(false)
const answering = ref(false)
const uploadingImages = ref(false)
const editorTab = ref('edit')
const imageInputRef = ref<HTMLInputElement | null>(null)
const postImages = ref<string[]>([])
const postLocation = ref('')
const postRoute = ref('')
const postVisibility = ref<'public' | 'private'>('public')
const allowComments = ref(true)
const publishAgreed = ref(true)
const COMMUNITY_IMAGE_BASE_URL = 'https://objectstorage.ap-tokyo-1.oraclecloud.com/p/5R94ZnD93iYTCorrhHEGgkXcgT2tu6J_BD46w3gCc3oJeUa-r-C82LOvxrDvMxE/n/nrrguvtqppqi/b/ortus-bucket/o/'

const postForm = reactive({ title: '', content: '', images: '' })
const questionForm = reactive({ title: '', content: '' })
const locationForm = reactive({ destination: '', route: '' })
const visibilityText = computed(() => postVisibility.value === 'public' ? '公开可见' : '私密可见')

const loadPosts = async () => {
  try {
    const page = await getPosts({ page: 1, size: 20, keyword: postKeyword.value, sort: postSort.value })
    posts.value = page.list || []
  } catch {}
}

const loadQuestions = async () => {
  try {
    const page = await getQuestions({ page: 1, size: 20, keyword: questionKeyword.value })
    questions.value = page.list || []
  } catch {}
}

const openCreatePost = () => {
  editingPost.value = null
  Object.assign(postForm, { title: '', content: '', images: '' })
  postImages.value = []
  postLocation.value = ''
  postRoute.value = ''
  Object.assign(locationForm, { destination: '', route: '' })
  postVisibility.value = 'public'
  allowComments.value = true
  editorTab.value = 'edit'
  publishAgreed.value = true
  showPostDialog.value = true
}

const openEditPost = (post: Post) => {
  if (!canEditPost(post)) return
  editingPost.value = { ...post }
  Object.assign(postForm, {
    title: post.title,
    content: stripPostMeta(post.content),
    images: post.images || ''
  })
  postImages.value = imageList(post.images)
  postLocation.value = locationLabel(post)
  postRoute.value = ''
  Object.assign(locationForm, { destination: postLocation.value, route: '' })
  postVisibility.value = post.status === 0 ? 'private' : 'public'
  allowComments.value = allowCommentsForPost(post)
  editorTab.value = 'edit'
  publishAgreed.value = true
  showPostDialog.value = true
}

const handleSubmitPost = async () => {
  if (!postForm.title.trim() || !postForm.content.trim()) {
    ElMessage.warning('请填写标题和正文')
    return
  }
  if (!publishAgreed.value) {
    ElMessage.warning('请先同意社区发布规则')
    return
  }
  posting.value = true
  try {
    const isEditing = Boolean(editingPost.value?.id)
    const payload: Post = {
      title: postForm.title,
      content: buildPostContentForSubmit(),
      images: JSON.stringify(postImages.value),
      likes: editingPost.value?.likes || 0,
      comments_count: editingPost.value?.comments_count || 0,
      status: postVisibility.value === 'public' ? 1 : 0
    }
    if (isEditing && editingPost.value?.id) {
      await updatePost(editingPost.value.id, payload)
    } else {
      await createPost(payload)
    }
    showPostDialog.value = false
    await loadPosts()
    ElMessage.success(isEditing ? '游记已更新' : '游记已发布')
  } catch {
  } finally {
    posting.value = false
  }
}

const openPostReader = async (post: Post) => {
  if (!post.id) return
  try {
    showPostReader.value = true
    postDetail.value = post
    selectedPost.value = post
    commentContent.value = ''
    comments.value = []
    const [detail, page] = await Promise.all([
      getPost(post.id),
      getPostComments(post.id, { page: 1, size: 50 })
    ])
    postDetail.value = detail
    selectedPost.value = detail
    comments.value = page.list || []
  } catch {}
}

const handleDeletePost = async (post: Post) => {
  if (!post.id || !canDeletePost(post)) return
  try {
    await ElMessageBox.confirm('确认删除这篇游记？', '删除游记', { type: 'warning' })
    await deletePost(post.id)
    if (postDetail.value?.id === post.id) showPostReader.value = false
    posts.value = posts.value.filter(item => item.id !== post.id)
    ElMessage.success('游记已删除')
    loadPosts()
  } catch (error) {
    if (error !== 'cancel') {}
  }
}

const handleLike = async (post: Post) => {
  if (!post.id) return
  try {
    const result = await togglePostLike(post.id)
    post.likes = result.likes
    post.liked = result.liked
    if (postDetail.value?.id === post.id) {
      postDetail.value.likes = result.likes
      postDetail.value.liked = result.liked
    }
    ElMessage.success(result.liked ? '已点赞' : '已取消点赞')
  } catch {}
}

const handleCreateComment = async () => {
  if (!selectedPost.value?.id || !commentContent.value.trim()) return
  if (!allowCommentsForPost(selectedPost.value)) {
    ElMessage.warning('该游记已关闭评论')
    return
  }
  commenting.value = true
  try {
    const created = await createPostComment(selectedPost.value.id, { content: commentContent.value })
    comments.value.push(created)
    selectedPost.value.comments_count = (selectedPost.value.comments_count || 0) + 1
    if (postDetail.value) postDetail.value.comments_count = selectedPost.value.comments_count
    const listPost = posts.value.find(item => item.id === selectedPost.value?.id)
    if (listPost) listPost.comments_count = selectedPost.value.comments_count
    commentContent.value = ''
    ElMessage.success('评论已发送')
  } catch {
  } finally {
    commenting.value = false
  }
}

const handleSharePost = async (post: Post) => {
  if (!post.id) return
  const url = `${window.location.origin}${window.location.pathname}#/community?postId=${encodeURIComponent(post.id)}`
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(url)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = url
      textarea.style.position = 'fixed'
      textarea.style.left = '-9999px'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    ElMessage.success('链接已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动复制当前页面链接')
  }
}

const handleDeleteComment = async (comment: Comment) => {
  if (!selectedPost.value?.id || !comment.id || !canDeleteComment(comment)) return
  try {
    await ElMessageBox.confirm('确认删除这条评论？', '删除评论', { type: 'warning' })
    const postId = selectedPost.value.id
    await deletePostComment(postId, comment.id)
    comments.value = comments.value.filter(item => item.id !== comment.id)
    selectedPost.value.comments_count = Math.max((selectedPost.value.comments_count || 0) - 1, 0)
    if (postDetail.value?.id === postId) postDetail.value.comments_count = selectedPost.value.comments_count
    const listPost = posts.value.find(item => item.id === postId)
    if (listPost) listPost.comments_count = selectedPost.value.comments_count
    ElMessage.success('评论已删除')
  } catch (error) {
    if (error !== 'cancel') {}
  }
}

const handleCreateQuestion = async () => {
  if (!questionForm.title.trim() || !questionForm.content.trim()) {
    ElMessage.warning('请填写问题标题和描述')
    return
  }
  questioning.value = true
  try {
    await createQuestion({ title: questionForm.title, content: questionForm.content })
    Object.assign(questionForm, { title: '', content: '' })
    showQuestionDialog.value = false
    await loadQuestions()
    ElMessage.success('问题已发布')
  } catch {
  } finally {
    questioning.value = false
  }
}

const openAnswer = (question: Question) => {
  selectedQuestion.value = question
  answerContent.value = question.answer || ''
  showAnswerDialog.value = true
}

const handleAnswerQuestion = async () => {
  if (!selectedQuestion.value?.id || !answerContent.value.trim()) return
  answering.value = true
  try {
    await answerQuestion(selectedQuestion.value.id, answerContent.value)
    showAnswerDialog.value = false
    await loadQuestions()
    ElMessage.success('回答已提交')
  } catch {
  } finally {
    answering.value = false
  }
}

const handleDeleteQuestion = async (question: Question) => {
  if (!question.id || !canDeleteQuestion(question)) return
  try {
    await ElMessageBox.confirm('确认删除这个问题？', '删除问题', { type: 'warning' })
    await deleteQuestion(question.id)
    await loadQuestions()
    ElMessage.success('问题已删除')
  } catch (error) {
    if (error !== 'cancel') {}
  }
}

const handleDeleteAnswer = async (question: Question) => {
  if (!question.id || !canDeleteAnswer(question)) return
  try {
    await ElMessageBox.confirm('确认删除这个回答？', '删除回答', { type: 'warning' })
    await deleteQuestionAnswer(question.id)
    await loadQuestions()
    ElMessage.success('回答已删除')
  } catch (error) {
    if (error !== 'cancel') {}
  }
}

const firstImage = (images?: string) => {
  const objectName = imageList(images)[0]
  return objectName ? communityImageUrl(objectName) : ''
}

const communityImageName = (value: string) => {
  const normalized = value.trim().split(/[?#]/, 1)[0].replace(/\\/g, '/')
  if (/^https?:\/\//i.test(normalized) && !normalized.includes('objectstorage.ap-tokyo-1.oraclecloud.com/')) {
    return normalized
  }
  return normalized.substring(normalized.lastIndexOf('/') + 1)
}

const communityImageUrl = (objectName: string) => {
  const normalized = communityImageName(objectName)
  return /^https?:\/\//i.test(normalized)
    ? normalized
    : `${COMMUNITY_IMAGE_BASE_URL}${encodeURIComponent(normalized)}`
}

const imageList = (images?: string) => {
  if (!images) return []
  try {
    const parsed = JSON.parse(images)
    return Array.isArray(parsed) ? parsed.map(String).map(communityImageName).filter(Boolean) : []
  } catch {
    return []
  }
}

const validateImageFile = (file: File) => {
  if (!file.type.startsWith('image/')) {
    ElMessage.warning(`${file.name} 不是图片文件`)
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning(`${file.name} 超过 5MB`)
    return false
  }
  return true
}

const compressImage = async (file: File) => {
  const isSmallJpeg = /jpe?g/i.test(file.type) && file.size <= 900 * 1024
  if (isSmallJpeg) return file
  const image = await loadImage(file)
  const maxSide = 1600
  const scale = Math.min(1, maxSide / Math.max(image.width, image.height))
  const width = Math.max(1, Math.round(image.width * scale))
  const height = Math.max(1, Math.round(image.height * scale))
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const context = canvas.getContext('2d')
  if (!context) return file
  context.fillStyle = '#fff'
  context.fillRect(0, 0, width, height)
  context.drawImage(image, 0, 0, width, height)
  const blob = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.82))
  if (!blob) return file
  const compressed = new File([blob], replaceImageExt(file.name), { type: 'image/jpeg' })
  return compressed.size < file.size ? compressed : file
}

const loadImage = (file: File) => new Promise<HTMLImageElement>((resolve, reject) => {
  const url = URL.createObjectURL(file)
  const image = new Image()
  image.onload = () => {
    URL.revokeObjectURL(url)
    resolve(image)
  }
  image.onerror = () => {
    URL.revokeObjectURL(url)
    reject(new Error('invalid image'))
  }
  image.src = url
})

const replaceImageExt = (name: string) => name.replace(/\.[^.]+$/, '') + '.jpg'

const handleImageFiles = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || []).filter(validateImageFile)
  if (!files.length) return
  const remainingSlots = 9 - postImages.value.length
  if (remainingSlots <= 0) {
    ElMessage.warning('最多上传 9 张图片')
    input.value = ''
    return
  }
  const uploadFiles = files.slice(0, remainingSlots)
  if (uploadFiles.length < files.length) {
    ElMessage.warning('最多上传 9 张图片，已自动忽略超出的图片')
  }
  uploadingImages.value = true
  try {
    for (const file of uploadFiles) {
      const uploadFile = await compressImage(file)
      const result = await uploadCommunityImage(uploadFile)
      postImages.value.push(result.objectName)
    }
    ElMessage.success('图片已上传')
  } catch {
  } finally {
    uploadingImages.value = false
    input.value = ''
  }
}

const removePostImage = (image: string) => {
  postImages.value = postImages.value.filter(item => item !== image)
}

const insertTopic = () => {
  postForm.content = `${postForm.content.trim()}\n\n#旅行游记 #城市漫步`.trim()
}

const handleLocationSubmit = () => {
  postLocation.value = locationForm.destination.trim()
  postRoute.value = locationForm.route.trim()
  const tags = [postLocation.value, postRoute.value]
    .filter(Boolean)
    .map(item => `#${item.replace(/\s+/g, '')}`)
  if (tags.length) {
    const appendText = tags.filter(tag => !postForm.content.includes(tag)).join(' ')
    if (appendText) postForm.content = `${postForm.content.trim()}\n\n${appendText}`.trim()
  }
  showLocationDialog.value = false
}

const saveDraft = () => ElMessage.info('草稿已保留在当前编辑框')

const renderMarkdown = (value: string, hideStandaloneTags = false) => {
  const content = hideStandaloneTags ? stripStandaloneTagLines(stripPostMeta(value || '')) : stripPostMeta(value || '')
  const escaped = escapeHtml(content)
  return escaped.split(/\n{2,}/).map(block => renderMarkdownBlock(block)).join('')
}

const renderMarkdownBlock = (block: string) => {
  if (/^#{1,3}\s/.test(block)) {
    const level = Math.min((block.match(/^#+/)?.[0].length || 2), 3)
    const text = inlineMarkdown(block.replace(/^#{1,3}\s*/, ''))
    return `<h${level}>${text}</h${level}>`
  }
  if (block.startsWith('&gt; ')) return `<blockquote>${inlineMarkdown(block.replace(/^&gt;\s*/, ''))}</blockquote>`
  if (/^-\s/m.test(block)) {
    const items = block.split('\n').filter(line => line.startsWith('- ')).map(line => `<li>${inlineMarkdown(line.slice(2))}</li>`).join('')
    return `<ul>${items}</ul>`
  }
  return `<p>${inlineMarkdown(block).replace(/\n/g, '<br>')}</p>`
}

const inlineMarkdown = (value: string) => value
  .replace(/!\[([^\]]*)\]\((https?:\/\/[^)\s]+)\)/g, '<img src="$2" alt="$1" />')
  .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  .replace(/`([^`]+)`/g, '<code>$1</code>')
  .replace(/(^|[\s>])#([\u4e00-\u9fa5A-Za-z0-9_-]+)/g, '$1<span class="topic-token">#$2</span>')

const escapeHtml = (value: string) => value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
const plainText = (value: string) => stripStandaloneTagLines(stripPostMeta(value || '')).replace(/!\[[^\]]*]\([^)]+\)/g, '').replace(/[#*_`>]/g, '').replace(/\s+/g, ' ').trim().slice(0, 88)
const formatTime = (value?: string) => value ? value.slice(0, 10) : '刚刚'
const displayNickname = (nickname?: string, fallbackId?: string) => nickname?.trim() || (fallbackId ? `用户${fallbackId}` : '-')
const avatarLetter = (nickname?: string, fallbackId?: string) => displayNickname(nickname, fallbackId).slice(0, 1).toUpperCase()
const normalizeUserId = (userId?: string) => {
  const value = String(userId || '').trim()
  return /^\d+$/.test(value) ? String(Number(value)) : value
}
const isOwner = (userId?: string) => Boolean(userId && authStore.userId && normalizeUserId(userId) === normalizeUserId(authStore.userId))
const canEditPost = (post: Post) => isOwner(post.user_id)
const canDeletePost = (post: Post) => authStore.isAdmin || isOwner(post.user_id)
const canDeleteComment = (comment: Comment) => authStore.isAdmin || isOwner(comment.user_id) || isOwner(selectedPost.value?.user_id)
const canDeleteQuestion = (question: Question) => authStore.isAdmin || isOwner(question.user_id)
const canAnswerQuestion = (question: Question) => {
  const answered = Boolean(question.answer || question.answer_user_id || question.status === 1)
  return !answered || authStore.isAdmin || isOwner(question.answer_user_id)
}
const canDeleteAnswer = (question: Question) => {
  const answered = Boolean(question.answer || question.answer_user_id || question.status === 1)
  return answered && (authStore.isAdmin || isOwner(question.answer_user_id))
}
const locationLabel = (post: Post) => contentTags(post)[0] || post.title.split(/[，,：:｜|]/)[0] || '旅行目的地'
const locationPills = (post: Post) => Array.from(new Set([locationLabel(post), '旅行游记'].filter(Boolean)))
const contentTags = (post: Post) => {
  const matches = post.content?.match(/#[\u4e00-\u9fa5A-Za-z0-9_-]+/g) || []
  return Array.from(new Set(matches.map(item => item.replace(/^#/, '')))).filter(tag => tag !== '关闭评论').slice(0, 6)
}
const readerTags = (post: Post) => contentTags(post).slice(0, 6)
const allowCommentsForPost = (post: Post) => !post.content?.includes('#关闭评论')
const buildPostContentForSubmit = () => {
  let content = stripPostMeta(postForm.content).trim()
  if (!allowComments.value) content = `${content}\n\n#关闭评论`.trim()
  return content
}
const stripPostMeta = (value: string) => value.replace(/(^|\s)#关闭评论(?=\s|$)/g, ' ').trim()
const stripStandaloneTagLines = (value: string) => value.split('\n').filter(line => !/^(\s*#[\u4e00-\u9fa5A-Za-z0-9_-]+)+\s*$/.test(line.trim())).join('\n').trim()

onMounted(() => {
  authStore.hydrate()
  loadPosts()
  loadQuestions()
})
</script>

<style scoped>
/* ── Layout (uses global .module-page, .module-hero, .hero-inner) ── */
.community-section { padding: 30px 0 56px; }
.community-tabs { background: var(--bg-card); border: 1px solid var(--border-light); border-radius: var(--radius-sm); padding: 18px; }
.toolbar { display: grid; gap: 12px; grid-template-columns: 1fr 140px auto; margin-bottom: 18px; }
.question-toolbar { grid-template-columns: 1fr auto auto; }

/* ── Post grid ── */
.post-grid { column-count: 3; column-gap: 18px; }
.post-card { background: var(--bg-card); border: 1px solid var(--border-light); border-radius: var(--radius-sm); break-inside: avoid; cursor: pointer; margin: 0 0 18px; overflow: hidden; transition: transform .18s ease, box-shadow .18s ease; }
.post-card:hover { box-shadow: var(--shadow-md); transform: translateY(-2px); }
.post-cover { background: var(--cream-100); min-height: 180px; position: relative; }
.post-cover img { display: block; object-fit: cover; width: 100%; }
.empty-cover { align-items: center; aspect-ratio: 4 / 3; color: var(--slate-400); display: flex; font-weight: 800; justify-content: center; }
.photo-count, .cover-label { background: rgba(10,22,40,0.72); border-radius: 999px; color: var(--white); font-size: 12px; padding: 4px 8px; position: absolute; right: 10px; top: 10px; }
.cover-label { background: var(--gold-500); left: 0; right: auto; top: 0; }
.post-body { padding: 14px; }
.post-body h2 { color: var(--navy-900); font-size: 17px; line-height: 1.45; margin-bottom: 8px; }
.post-body p { color: var(--text-secondary); font-size: 14px; line-height: 1.65; }
.post-author-line, .post-actions { align-items: center; color: var(--slate-500); display: flex; flex-wrap: wrap; font-size: 13px; gap: 8px; margin-top: 12px; }
.mini-avatar { align-items: center; background: linear-gradient(135deg, var(--gold-200), var(--cream-100)); border-radius: 50%; color: var(--accent); display: inline-flex; font-weight: 800; height: 26px; justify-content: center; width: 26px; }
.dot { color: var(--slate-300); }
.liked-action { font-weight: 700; }
.native-file-input { display: none; }

/* ── Publish dialog ── */
.trip-publish-dialog :deep(.el-dialog) { border-radius: var(--radius-sm); max-width: calc(100vw - 24px); }
.trip-publish-dialog :deep(.el-dialog__header) { display: none; }
.trip-publish-dialog :deep(.el-dialog__body) { padding: 0; }
.trip-publish { background: var(--bg-card); color: var(--text-primary); max-height: 88vh; overflow: auto; padding: 0 22px 22px; }
.publish-topbar { align-items: center; background: var(--bg-card); display: flex; gap: 14px; justify-content: space-between; padding: 18px 0; position: sticky; top: 0; z-index: 2; }
.icon-button, .plain-button { background: none; border: 0; color: var(--text-primary); cursor: pointer; }
.icon-button { font-size: 38px; line-height: 1; }
.plain-button { color: var(--accent); font-size: 14px; }
.quality-tip { background: linear-gradient(90deg, var(--gold-200), var(--cream-100)); color: var(--navy-700); font-weight: 700; margin-bottom: 18px; padding: 12px 14px; border-radius: var(--radius-sm); }
.cover-strip { display: flex; gap: 12px; overflow-x: auto; padding-bottom: 12px; }
.cover-tile, .upload-tile { border-radius: var(--radius-sm); flex: 0 0 116px; height: 116px; overflow: hidden; position: relative; }
.cover-tile img { height: 100%; object-fit: cover; width: 100%; }
.remove-image { align-items: center; background: rgba(0,0,0,.48); border: 0; border-radius: 50%; color: var(--white); cursor: pointer; display: flex; font-size: 18px; height: 28px; justify-content: center; position: absolute; right: 6px; top: 6px; width: 28px; }
.upload-tile { align-items: center; background: var(--cream-50); border: 1px dashed var(--slate-200); color: var(--slate-400); cursor: pointer; display: flex; flex-direction: column; font-size: 15px; gap: 8px; justify-content: center; }
.camera-mark { font-size: 32px; line-height: 1; }
.trip-title-input, .trip-content-input { border: 0; border-bottom: 1px solid var(--border-light); box-sizing: border-box; color: var(--text-primary); font: inherit; outline: none; padding: 16px 4px; width: 100%; }
.trip-title-input { font-size: 24px; font-weight: 800; }
.trip-title-input::placeholder, .trip-content-input::placeholder { color: var(--slate-300); }
.trip-content-input { border-bottom: 0; line-height: 1.9; min-height: 240px; resize: vertical; }
.assistant-row { display: flex; flex-wrap: wrap; gap: 10px; margin: 12px 0 18px; }
.assistant-row button { background: var(--cream-100); border: 0; border-radius: 999px; color: var(--accent); cursor: pointer; font-weight: 800; padding: 10px 15px; }
.publish-row { align-items: center; background: transparent; border: 0; border-top: 1px solid var(--border-light); color: var(--text-primary); cursor: pointer; display: grid; gap: 12px; grid-template-columns: 24px auto 1fr; padding: 18px 2px; text-align: left; width: 100%; }
.publish-row span:last-child { color: var(--text-muted); justify-self: end; }
.row-icon { color: var(--accent); }
.publish-agreement { align-items: center; color: var(--text-secondary); display: flex; gap: 10px; padding: 10px 0 18px; }
.advanced-options { display: grid; gap: 12px; }
.advanced-option { align-items: center; background: var(--cream-50); border: 1px solid var(--border-light); border-radius: var(--radius-sm); display: flex; gap: 16px; justify-content: space-between; padding: 14px; }
.advanced-option p { color: var(--text-secondary); font-size: 13px; line-height: 1.5; margin-top: 4px; }
.publish-footer { align-items: center; background: var(--bg-card); bottom: 0; display: grid; gap: 12px; grid-template-columns: 90px 1fr; padding-top: 12px; position: sticky; }
.draft-button, .publish-button { border: 0; border-radius: 999px; cursor: pointer; font-weight: 800; min-height: 52px; }
.draft-button { background: var(--cream-100); color: var(--slate-700); }
.publish-button { background: linear-gradient(135deg, var(--accent), var(--accent-hover)); color: var(--white); font-size: 18px; }
.publish-button:disabled { cursor: not-allowed; opacity: .55; }

/* ── Reader ── */
.trip-reader-drawer :deep(.el-drawer__body) { padding: 0; }
.trip-reader { background: var(--bg-card); min-height: 100%; padding: 0 34px 120px; }
.reader-topbar { align-items: center; background: var(--bg-card); display: flex; gap: 14px; justify-content: space-between; padding: 18px 0; position: sticky; top: 0; z-index: 2; }
.reader-author { align-items: center; display: flex; flex: 1; gap: 12px; }
.reader-avatar { align-items: center; background: linear-gradient(135deg, var(--gold-200), var(--cream-100)); border-radius: 50%; color: var(--accent); display: inline-flex; font-weight: 800; height: 44px; justify-content: center; width: 44px; font-size: 18px; }
.follow-button { background: transparent; border: 1px solid var(--accent); border-radius: 999px; color: var(--accent); cursor: pointer; font-weight: 800; padding: 8px 18px; }
.reader-carousel { margin: 18px auto 8px; max-width: 520px; }
.reader-carousel img { height: 100%; object-fit: cover; width: 100%; }
.reader-location-row { display: flex; flex-wrap: wrap; gap: 10px; margin: 12px 0 24px; }
.location-pill { background: var(--cream-100); border-radius: 999px; color: var(--text-secondary); padding: 9px 14px; }
.reader-title { color: var(--text-primary); font-size: 28px; line-height: 1.45; margin-bottom: 8px; }
.reader-time { color: var(--text-muted); margin-bottom: 18px; }
.markdown-preview { color: var(--text-primary); line-height: 2; }
.markdown-preview :deep(p), .markdown-preview :deep(ul), .markdown-preview :deep(blockquote) { margin: 14px 0; }
.markdown-preview :deep(ul) { padding-left: 22px; }
.markdown-preview :deep(blockquote) { background: var(--cream-100); border-left: 3px solid var(--accent); padding: 8px 12px; border-radius: 0 var(--radius-sm) var(--radius-sm) 0; }
.markdown-preview :deep(img) { border-radius: var(--radius-sm); display: block; margin: 12px 0; max-width: 100%; }
.markdown-preview :deep(code) { background: var(--cream-100); border-radius: 4px; padding: 2px 5px; }
.markdown-preview :deep(.topic-token) { color: var(--accent); font-weight: 800; }
.reader-tags { display: flex; flex-wrap: wrap; gap: 14px; margin: 24px 0; }
.reader-tags span { color: var(--accent); font-weight: 700; }
.reader-owner-actions { border-bottom: 1px solid var(--border-light); margin-bottom: 20px; padding-bottom: 16px; }
.reader-comments h3 { font-size: 18px; margin-bottom: 12px; }
.comment-item { border-bottom: 1px solid var(--border-light); margin-bottom: 10px; padding-bottom: 10px; }
.comment-heading { align-items: center; display: flex; justify-content: space-between; }
.comment-item p { color: var(--text-secondary); margin-top: 4px; }
.reader-bottom-bar { background: var(--bg-elevated); border-top: 1px solid var(--border-light); bottom: 0; display: grid; gap: 8px; left: auto; max-width: 760px; padding: 10px 18px; position: fixed; right: 0; width: min(760px, 100vw); z-index: 20; }
.comment-send-row { align-items: center; display: grid; gap: 8px; grid-template-columns: minmax(0, 1fr) auto; }
.reader-action-row { align-items: center; display: flex; gap: 22px; justify-content: flex-end; }
.reader-bottom-bar button { background: transparent; border: 0; color: var(--text-primary); cursor: pointer; font-weight: 800; }
.reader-bottom-bar button:disabled { color: var(--slate-300); cursor: not-allowed; }
.send-comment-button { background: var(--accent) !important; border-radius: 999px; color: var(--white) !important; min-width: 64px; padding: 8px 14px; }
.send-comment-button:disabled { background: var(--slate-200) !important; color: var(--white) !important; }
.reader-bottom-bar button.active { color: var(--accent); }

/* ── Questions ── */
.question-list { display: grid; gap: 14px; }
.question-card { align-items: flex-start; background: var(--bg-card); border: 1px solid var(--border-light); border-radius: var(--radius-sm); display: flex; justify-content: space-between; padding: 18px; }
.question-card h2 { font-size: 18px; margin-bottom: 8px; color: var(--text-primary); }
.question-card p, .question-meta { color: var(--text-secondary); }
.question-meta { display: flex; flex-wrap: wrap; font-size: 13px; gap: 14px; margin-top: 12px; }
.question-side { align-items: flex-end; display: flex; flex-direction: column; gap: 12px; }
.answer-box { background: var(--cream-100); border-left: 3px solid var(--accent); color: var(--text-primary); margin-top: 12px; padding: 12px; border-radius: 0 var(--radius-sm) var(--radius-sm) 0; }
.answer-meta { color: var(--text-secondary); font-size: 13px; margin-bottom: 6px; }
.answer-title { color: var(--text-secondary); margin-bottom: 12px; }

/* ── Floating publish ── */
.floating-publish { align-items: center; background: var(--accent); border: 0; border-radius: 50%; bottom: 96px; box-shadow: 0 12px 28px rgba(201,149,61,0.28); color: var(--white); cursor: pointer; display: flex; font-size: 30px; font-weight: 300; height: 56px; justify-content: center; position: fixed; right: 28px; width: 56px; z-index: 9; transition: transform var(--duration-fast) var(--ease-out); }
.floating-publish:hover { transform: scale(1.08); }

@media (max-width: 980px) { .post-grid { column-count: 2; } }
@media (max-width: 760px) {
  .hero-inner, .question-card { align-items: flex-start; flex-direction: column; }
  .toolbar, .question-toolbar { grid-template-columns: 1fr; }
  .post-grid { column-count: 1; }
  .trip-reader { padding: 0 18px 120px; }
  .reader-carousel { max-width: 100%; }
  .reader-bottom-bar { left: 0; max-width: none; padding: 10px; width: 100vw; }
  .reader-action-row { gap: 14px; }
}
</style>
