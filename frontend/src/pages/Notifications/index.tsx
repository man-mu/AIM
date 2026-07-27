import { CheckOutlined, DeleteOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { notificationApi } from '@/apis/notification';
import { queryKeys } from '@/apis/queryKeys';
import { EmptyState } from '@/components/ui/EmptyState';
import { Spinner } from '@/components/ui/Spinner';
import { toast } from '@/components/ui/toast/toastBus';
import { formatConversationStamp } from '@/lib/datetime';
import { toInt64String } from '@/lib/ids';
import type { NotificationDTO } from '@/types/Notification/Notification';

/** 通知中心：全部/未读筛选、逐条已读/删除、一键全读；好友申请通知可跳转处理。 */
export default function Notifications(): React.JSX.Element {
  const [onlyUnread, setOnlyUnread] = useState(false);
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const list = useQuery({
    queryKey: [...queryKeys.notifications.list, onlyUnread] as const,
    queryFn: async ({ signal }) => {
      const data = await notificationApi.list(
        { pageNum: 1, pageSize: 50, ...(onlyUnread ? { isRead: false } : {}) },
        { signal },
      );
      return data.list;
    },
    staleTime: 15_000,
  });

  const invalidate = (): void => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all });
  };
  const onError = (error: unknown): void => {
    toast.error(error instanceof Error ? error.message : '操作失败');
  };

  const markRead = useMutation({
    mutationFn: (id: string) => notificationApi.markRead(id),
    onSuccess: invalidate,
    onError,
  });
  const readAll = useMutation({
    mutationFn: () => notificationApi.readAll(),
    onSuccess: () => {
      toast.success('已全部标记为已读');
      invalidate();
    },
    onError,
  });
  const remove = useMutation({
    mutationFn: (id: string) => notificationApi.delete(id),
    onSuccess: invalidate,
    onError,
  });

  const open = (notification: NotificationDTO): void => {
    if (!notification.isRead) {
      markRead.mutate(toInt64String(notification.id));
    }
    // 好友申请类通知 → 联系人处理页。
    if (notification.title.includes('好友')) {
      void navigate('/contacts?tab=requests');
    }
  };

  return (
    <div className="h-full overflow-y-auto bg-[#f5f5f7]">
      <div className="mx-auto max-w-2xl p-5">
        <header className="mb-4 flex items-center gap-2">
          <h1 className="min-w-0 flex-1 text-base font-semibold text-[#1d1d1f]">通知</h1>
          <div role="radiogroup" aria-label="筛选" className="flex gap-1 rounded-lg bg-black/[0.05] p-1">
            {([
              [false, '全部'],
              [true, '未读'],
            ] as const).map(([value, label]) => (
              <button
                key={label}
                type="button"
                role="radio"
                aria-checked={onlyUnread === value}
                onClick={() => setOnlyUnread(value)}
                className={`rounded-md px-3 py-1 text-xs font-medium transition ${
                  onlyUnread === value ? 'bg-white text-[#1d1d1f] shadow-sm' : 'text-[#6e6e73] hover:text-[#1d1d1f]'
                }`}
              >
                {label}
              </button>
            ))}
          </div>
          <button
            type="button"
            onClick={() => readAll.mutate()}
            className="flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-medium text-[#0071e3] transition hover:bg-[#0071e3]/8"
          >
            <CheckOutlined aria-hidden />
            全部已读
          </button>
        </header>

        {list.isLoading ? (
          <div className="flex justify-center py-16">
            <Spinner size={20} />
          </div>
        ) : (list.data ?? []).length === 0 ? (
          <EmptyState title={onlyUnread ? '没有未读通知' : '暂无通知'} />
        ) : (
          <ul className="m-0 grid list-none gap-1 p-0">
            {(list.data ?? []).map((notification) => {
              const id = toInt64String(notification.id);
              return (
                <li
                  key={id}
                  className="group/notification flex items-start gap-3 rounded-xl bg-white px-4 py-3 shadow-[0_1px_2px_rgba(0,0,0,0.04)]"
                  style={{ contentVisibility: 'auto', containIntrinsicSize: 'auto 72px' } as React.CSSProperties}
                >
                  <span aria-hidden className={`mt-1.5 size-2 shrink-0 rounded-full ${notification.isRead ? 'bg-transparent' : 'bg-[#0071e3]'}`} />
                  <button type="button" onClick={() => open(notification)} className="min-w-0 flex-1 text-left">
                    <span className="flex items-baseline justify-between gap-2">
                      <span className={`min-w-0 truncate text-sm ${notification.isRead ? 'text-[#6e6e73]' : 'font-medium text-[#1d1d1f]'}`}>
                        {notification.title}
                      </span>
                      <time className="shrink-0 text-[11px] text-[#a1a1a6]">
                        {formatConversationStamp(notification.createdAt)}
                      </time>
                    </span>
                    <span className="mt-0.5 block truncate text-xs text-[#86868b]">{notification.content}</span>
                  </button>
                  <button
                    type="button"
                    aria-label="删除通知"
                    onClick={() => remove.mutate(id)}
                    className="grid size-7 shrink-0 place-items-center rounded-md text-[#c7c7cc] opacity-0 transition group-hover/notification:opacity-100 hover:bg-black/[0.05] hover:text-[#86868b]"
                  >
                    <DeleteOutlined aria-hidden />
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}
