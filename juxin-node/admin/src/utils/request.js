import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const service = axios.create({
    baseURL: '',
    timeout: 60000
})

// request interceptor
service.interceptors.request.use(
    config => {
        const token = localStorage.getItem('juxin_node_admin_token')
        if (token) {
            config.headers['token'] = token
            config.headers['Authorization'] = `Bearer ${token}`
        }
        return config
    },
    error => {
        return Promise.reject(error)
    }
)

// response interceptor
service.interceptors.response.use(
    response => {
        const res = response.data
        // 401: Unauthorized
        if (res.code === 401 || res.code === 403) {
            localStorage.removeItem('juxin_node_admin_token')
            router.push('/login')
            ElMessage.error(res.msg || '认证失败，请重新登录')
            return Promise.reject(new Error(res.msg || 'Error'))
        }
        return response
    },
    error => {
        if (error.response && (error.response.status === 401 || error.response.status === 403)) {
            localStorage.removeItem('juxin_node_admin_token')
            router.push('/login')
            ElMessage.error('认证过期，请重新登录')
        } else if (!error.config?.silent) {
            ElMessage.error(error.message || '系统错误')
        }
        return Promise.reject(error)
    }
)

export default service
