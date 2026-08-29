import { http } from '@/utils/request'
import type { PageResponse } from './itinerary'

export interface Post {
  id?: string
  user_id?: string
  user_nickname?: string
  title: string
  content: string
  images?: string
  likes: number
  liked?: boolean
  comments_count: number
  status?: number
  create_time?: string
  update_time?: string
}

export interface Comment {
  id?: string
  target_type?: string
  target_id?: string
  user_id?: string
  user_nickname?: string
  parent_id?: string
  content: string
  likes?: number
  is_approved?: number
  ip?: string
  create_time?: string
}

export interface Question {
  id?: string
  user_id?: string
  user_nickname?: string
  title: string
  content: string
  answer?: string
  answer_user_id?: string
  answer_user_nickname?: string
  status?: number
  create_time?: string
  answer_time?: string
}

export function getPosts(params?: Record<string, unknown>) {
  return http.get<PageResponse<Post>>('/posts', { params })
}

export function getPost(id: string) {
  return http.get<Post>(`/posts/${id}`)
}

export function createPost(data: Post) {
  return http.post<Post>('/posts', data)
}

export function updatePost(id: string, data: Post) {
  return http.put<Post>(`/posts/${id}`, data)
}

export function deletePost(id: string) {
  return http.delete<boolean>(`/posts/${id}`)
}

export function togglePostLike(id: string) {
  return http.post<{ liked: boolean; likes: number }>(`/posts/${id}/like`)
}

export function getPostComments(id: string, params?: Record<string, unknown>) {
  return http.get<PageResponse<Comment>>(`/posts/${id}/comments`, { params })
}

export function createPostComment(id: string, data: Comment) {
  return http.post<Comment>(`/posts/${id}/comments`, data)
}

export function deletePostComment(postId: string, commentId: string) {
  return http.delete<boolean>(`/posts/${postId}/comments/${commentId}`)
}

export function getQuestions(params?: Record<string, unknown>) {
  return http.get<PageResponse<Question>>('/questions', { params })
}

export function createQuestion(data: Question) {
  return http.post<Question>('/questions', data)
}

export function answerQuestion(id: string, answer: string) {
  return http.post<Question>(`/questions/${id}/answer`, { answer })
}

export function deleteQuestion(id: string) {
  return http.delete<boolean>(`/questions/${id}`)
}

export function deleteQuestionAnswer(id: string) {
  return http.delete<boolean>(`/questions/${id}/answer`)
}

export function uploadCommunityImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<{ objectName: string }>('/upload/image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}
