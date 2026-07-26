import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Dashboard from '../views/Dashboard.vue'
import DeviceMonitor from '../views/DeviceMonitor.vue'
import DeviceTasks from '../views/DeviceTasks.vue'
import Terminal from '../views/Terminal.vue'

const routes = [
    {
        path: '/login',
        name: 'Login',
        component: Login,
        meta: { requiresAuth: false }
    },
    {
        path: '/',
        name: 'Dashboard',
        component: Dashboard,
        meta: { requiresAuth: true },
        redirect: '/overview',
        children: [
            {
                path: 'overview',
                name: 'Overview',
                component: () => import('../views/Overview.vue'),
                meta: { title: '控制台概览' }
            },
            {
                path: 'monitor',
                name: 'DeviceMonitor',
                component: DeviceMonitor,
                meta: { title: '集群节点监控' }
            },
            {
                path: 'device-tasks',
                name: 'DeviceTasks',
                component: DeviceTasks,
                meta: { title: '算力生产记录' }
            },
            {
                path: 'device-commands',
                name: 'DeviceCommands',
                component: () => import('../views/DeviceCommands.vue'),
                meta: { title: '设备指令中心' }
            },
            {
                path: 'automation',
                name: 'Automation',
                component: () => import('../views/Automation.vue'),
                meta: { title: '任务编排自动化' }
            },
            {
                path: 'scheduling',
                name: 'Scheduling',
                component: () => import('../views/Scheduling.vue'),
                meta: { title: '智能调度策略' }
            },
            {
                path: 'operations',
                name: 'Operations',
                component: () => import('../views/Operations.vue'),
                meta: { title: '集群远程运维' }
            },
            {
                path: 'device-upgrades',
                name: 'DeviceUpgrades',
                component: () => import('../views/DeviceUpgrades.vue'),
                meta: { title: '设备升级管理' }
            },
            {
                path: 'terminal',
                name: 'Terminal',
                component: Terminal,
                meta: { title: '终端调试模式' }
            },
            {
                path: 'settings',
                name: 'Settings',
                component: () => import('../views/Settings.vue'),
                meta: { title: '系统配置中心' }
            }
        ]
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('orin_admin_token')

    if (to.meta.requiresAuth !== false && !token) {
        next('/login')
    } else if (to.path === '/login' && token) {
        next('/')
    } else {
        next()
    }
})

export default router
