import { create } from 'zustand';

/**
 * 工作台 UI 状态（纯客户端事实）：
 * 当前选中会话在 URL（/home/:conversationId），不入 store。
 */
interface WorkspaceState {
  /** 桌面端右侧详情面板开关。 */
  isDetailPanelOpen: boolean;
  /** 移动端视图：列表 ↔ 聊天。 */
  isMobileChatOpen: boolean;
  isCreateDialogOpen: boolean;
  toggleDetailPanel: () => void;
  setDetailPanelOpen: (open: boolean) => void;
  openMobileChat: () => void;
  closeMobileChat: () => void;
  setCreateDialogOpen: (open: boolean) => void;
}

export const useWorkspaceStore = create<WorkspaceState>((set) => ({
  isDetailPanelOpen: true,
  isMobileChatOpen: false,
  isCreateDialogOpen: false,
  toggleDetailPanel: () => set((state) => ({ isDetailPanelOpen: !state.isDetailPanelOpen })),
  setDetailPanelOpen: (open) => set({ isDetailPanelOpen: open }),
  openMobileChat: () => set({ isMobileChatOpen: true }),
  closeMobileChat: () => set({ isMobileChatOpen: false }),
  setCreateDialogOpen: (open) => set({ isCreateDialogOpen: open }),
}));
