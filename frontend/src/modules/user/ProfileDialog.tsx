import { useEffect, useRef, useState } from 'react';
import { Avatar } from '@/components/ui/Avatar';
import { Dialog } from '@/components/ui/Dialog';
import { Spinner } from '@/components/ui/Spinner';
import { toast } from '@/components/ui/toast/toastBus';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { toInt64String } from '@/lib/ids';
import { fileApi } from '@/apis/file';
import { mockBlobStore, isMockUploadUrl, fileIdFromMockUploadUrl } from '@/mocks/blobStore';
import { resizeAvatarToDataUrl } from '@/modules/file/image';
import type { Gender } from '@/types/User/User';
import { useChangePassword, useUpdateProfile } from './hooks';

/**
 * 个人资料：查看/编辑（头像、性别、简介）+ 修改密码。
 * 头像三步流：压缩(256px JPEG dataURL) → upload-url → PUT(mock 短路) → confirm → PUT /users/me。
 */
const inputClass =
  'w-full rounded-lg border border-black/[0.14] bg-white px-3 py-2 text-sm text-[#1d1d1f] outline-none transition placeholder:text-[#86868b] focus:border-[#0071e3] focus:ring-[3px] focus:ring-[#0071e3]/15';

const GENDER_OPTIONS: Array<{ value: Gender; label: string }> = [
  { value: 0, label: '保密' },
  { value: 1, label: '男' },
  { value: 2, label: '女' },
];

export function ProfileDialog({ open, onClose }: { open: boolean; onClose: () => void }): React.JSX.Element {
  const currentUser = useCurrentUser();
  const updateProfile = useUpdateProfile();
  const changePassword = useChangePassword();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [bio, setBio] = useState('');
  const [gender, setGender] = useState<Gender>(0);
  const [avatarUploading, setAvatarUploading] = useState(false);
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');

  useEffect(() => {
    if (open && currentUser) {
      setBio(currentUser.bio);
      setGender(currentUser.gender);
      setOldPassword('');
      setNewPassword('');
    }
  }, [open, currentUser]);

  const pickAvatar = async (file: File): Promise<void> => {
    setAvatarUploading(true);
    try {
      const dataUrl = await resizeAvatarToDataUrl(file);
      const blob = await (await fetch(dataUrl)).blob();

      // 三步流（与真实后端一致的调用序列）。
      const grant = await fileApi.getUploadUrl({
        name: 'avatar.jpg',
        mimeType: 'image/jpeg',
        size: blob.size,
        purpose: 2,
        access: 3,
      });
      if (isMockUploadUrl(grant.uploadUrl)) {
        mockBlobStore.putDataUrl(fileIdFromMockUploadUrl(grant.uploadUrl), dataUrl);
      }
      await fileApi.confirmUpload({ fileId: toInt64String(grant.fileId) });

      // dataURL 自包含，跨会话可持久渲染。
      await updateProfile.mutateAsync({ avatar: dataUrl });
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '头像更新失败');
    } finally {
      setAvatarUploading(false);
    }
  };

  const saveProfile = (): void => {
    updateProfile.mutate({ bio, gender });
  };

  const submitPassword = (): void => {
    if (newPassword.length < 6 || newPassword.length > 32) {
      toast.error('新密码需 6~32 个字符');
      return;
    }
    changePassword.mutate(
      { oldPassword, newPassword },
      {
        onSuccess: () => {
          setOldPassword('');
          setNewPassword('');
        },
      },
    );
  };

  return (
    <Dialog open={open} onClose={onClose} title="个人资料" maxWidth={440}>
      <div className="grid gap-6">
        <section className="flex items-center gap-4">
          <div className="relative">
            <Avatar
              name={currentUser?.username ?? '我'}
              src={currentUser?.avatar || undefined}
              colorKey={currentUser?.id}
              size="xl"
            />
            {avatarUploading ? (
              <span className="absolute inset-0 grid place-items-center rounded-full bg-white/60">
                <Spinner />
              </span>
            ) : null}
          </div>
          <div className="min-w-0">
            <p className="truncate text-base font-semibold">{currentUser?.username}</p>
            <p className="mt-0.5 truncate text-xs text-[#86868b]">{currentUser?.email || currentUser?.phone || '—'}</p>
            <button
              type="button"
              disabled={avatarUploading}
              onClick={() => fileInputRef.current?.click()}
              className="mt-2 rounded-lg border border-black/[0.12] px-3 py-1.5 text-xs font-medium text-[#424245] transition hover:border-black/[0.25] disabled:opacity-50"
            >
              更换头像
            </button>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              aria-label="选择头像图片"
              className="hidden"
              onChange={(event) => {
                const file = event.target.files?.[0];
                event.target.value = '';
                if (file) {
                  void pickAvatar(file);
                }
              }}
            />
          </div>
        </section>

        <section className="grid gap-3">
          <label className="grid gap-1.5 text-xs font-medium text-[#6e6e73]">
            个性签名
            <textarea
              value={bio}
              onChange={(event) => setBio(event.target.value)}
              rows={2}
              maxLength={80}
              placeholder="写点什么…"
              className={`${inputClass} resize-none`}
            />
          </label>
          <div className="grid gap-1.5 text-xs font-medium text-[#6e6e73]">
            性别
            <div role="radiogroup" aria-label="性别" className="flex gap-1 rounded-lg bg-black/[0.05] p-1">
              {GENDER_OPTIONS.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  role="radio"
                  aria-checked={gender === option.value}
                  onClick={() => setGender(option.value)}
                  className={`flex-1 rounded-md px-3 py-1.5 text-[13px] font-medium transition ${
                    gender === option.value ? 'bg-white text-[#1d1d1f] shadow-sm' : 'text-[#6e6e73] hover:text-[#1d1d1f]'
                  }`}
                >
                  {option.label}
                </button>
              ))}
            </div>
          </div>
          <button
            type="button"
            onClick={saveProfile}
            disabled={updateProfile.isPending}
            className="mt-1 h-10 rounded-lg bg-[#0071e3] text-sm font-medium text-white transition hover:bg-[#0077ed] disabled:opacity-50"
          >
            {updateProfile.isPending ? '保存中…' : '保存资料'}
          </button>
        </section>

        <section className="grid gap-3 border-t border-black/[0.06] pt-5">
          <p className="text-xs font-semibold text-[#6e6e73]">修改密码</p>
          <input
            type="password"
            value={oldPassword}
            onChange={(event) => setOldPassword(event.target.value)}
            placeholder="当前密码"
            autoComplete="current-password"
            aria-label="当前密码"
            className={inputClass}
          />
          <input
            type="password"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
            placeholder="新密码（6~32 位）"
            autoComplete="new-password"
            aria-label="新密码"
            className={inputClass}
          />
          <button
            type="button"
            onClick={submitPassword}
            disabled={changePassword.isPending || !oldPassword || !newPassword}
            className="h-10 rounded-lg border border-black/[0.12] text-sm font-medium text-[#424245] transition hover:border-black/[0.25] disabled:opacity-50"
          >
            {changePassword.isPending ? '提交中…' : '修改密码'}
          </button>
        </section>
      </div>
    </Dialog>
  );
}
