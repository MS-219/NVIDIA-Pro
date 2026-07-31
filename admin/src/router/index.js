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
                path: 'users',
                name: 'UserList',
                component: () => import('../views/UserList.vue'),
                meta: { title: '用户管理' }
            },
            {
                path: 'teams',
                name: 'TeamList',
                component: () => import('../views/TeamList.vue'),
                meta: { title: '团队管理' }
            },
            {
                path: 'notices',
                name: 'NoticeList',
                component: () => import('../views/NoticeList.vue'),
                meta: { title: '公告管理' }
            },
            {
                path: 'feedback',
                name: 'FeedbackList',
                component: () => import('../views/FeedbackList.vue'),
                meta: { title: '意见反馈' }
            },
            {
                path: 'withdrawals',
                name: 'WithdrawList',
                component: () => import('../views/WithdrawList.vue'),
                meta: { title: '提现管理' }
            },
            {
                path: 'payment-applies',
                name: 'PaymentApplyList',
                component: () => import('../views/PaymentApplyList.vue'),
                meta: { title: '账户变更审核' }
            },
            {
                path: 'earnings',
                name: 'EarningsList',
                component: () => import('../views/EarningsList.vue'),
                meta: { title: '收益管理' }
            },
            {
                path: 'rewards',
                name: 'RewardList',
                component: () => import('../views/RewardList.vue'),
                meta: { title: '分润流水' }
            },
            {
                path: 'device-settings',
                name: 'DeviceSettings',
                component: () => import('../views/Settings.vue'),
                meta: { title: '设备参数' }
            },
            {
                path: 'settings',
                name: 'PlatformSettings',
                component: () => import('../views/PlatformSettings.vue'),
                meta: { title: '系统设置' }
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
