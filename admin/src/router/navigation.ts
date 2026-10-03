import type { Component } from 'vue'
import { Odometer, Tools, FolderOpened, User, Bell, Picture, ChatLineSquare, Setting, Document } from '@element-plus/icons-vue'

export interface NavigationItem {
  path: string
  title: string
  description: string
  icon: Component
}

export const navigationGroups: { title: string; items: NavigationItem[] }[] = [
  { title: '工作空间', items: [
    { path: '/dashboard', title: '概览', description: '平台状态与管理入口', icon: Odometer },
    { path: '/tools', title: '工具管理', description: '管理工具信息、可用状态与展示顺序。', icon: Tools },
    { path: '/categories', title: '分类管理', description: '整理分类，让每个工具都容易被找到。', icon: FolderOpened },
    { path: '/users', title: '用户管理', description: '查看账号信息并管理用户访问权限。', icon: User },
  ] },
  { title: '内容与运营', items: [
    { path: '/announcements', title: '公告管理', description: '编辑与发布面向用户的公告。', icon: Bell },
    { path: '/banners', title: '推荐位管理', description: '管理首页推荐内容与展示顺序。', icon: Picture },
    { path: '/feedback', title: '用户反馈', description: '整理用户建议，跟进问题处理。', icon: ChatLineSquare },
  ] },
  { title: '平台设置', items: [
    { path: '/system-config', title: '系统配置', description: '管理平台运行所需的业务配置。', icon: Setting },
    { path: '/logs', title: '操作日志', description: '查看管理操作记录与变更历史。', icon: Document },
  ] },
]

export const navigationItems = navigationGroups.flatMap(group => group.items)
