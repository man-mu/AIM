import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { Avatar } from '@/components/ui/Avatar';
import { UnreadBadge } from '@/components/ui/Badge';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { Switch } from '@/components/ui/Switch';

/**
 * UI 基座组件测试。
 * （原 HomeShell 已被 AppLayout/ConversationWorkspace 取代，
 *  本文件转为覆盖被全应用复用的 ui 原语。）
 */
describe('Avatar', () => {
  it('renders the first character when no image is provided', () => {
    const { container } = render(<Avatar name="林川" colorKey="339394874048512101" />);
    expect(container.textContent).toBe('林');
  });

  it('renders an img when src exists', () => {
    const { container } = render(<Avatar name="林川" src="data:image/png;base64,x" />);
    expect(container.querySelector('img')).not.toBeNull();
  });

  it('keeps a stable background color for the same colorKey', () => {
    const first = render(<Avatar name="甲" colorKey="user-1" />).container.querySelector('span');
    const second = render(<Avatar name="乙" colorKey="user-1" />).container.querySelector('span');
    expect(first?.getAttribute('style')).toBe(second?.getAttribute('style'));
  });
});

describe('UnreadBadge', () => {
  it('hides at zero and caps at 99+', () => {
    const { container: empty } = render(<UnreadBadge count={0} />);
    expect(empty.textContent).toBe('');

    render(<UnreadBadge count={120} />);
    expect(screen.getByLabelText('未读 120 条')).toHaveTextContent('99+');
  });
});

describe('Switch', () => {
  it('toggles through the accessible checkbox', () => {
    const onChange = vi.fn();
    render(<Switch checked={false} onChange={onChange} label="置顶会话" />);

    fireEvent.click(screen.getByLabelText('置顶会话'));
    expect(onChange).toHaveBeenCalledWith(true);
  });
});

describe('ConfirmDialog', () => {
  it('fires onConfirm and onClose callbacks', () => {
    const onConfirm = vi.fn();
    const onClose = vi.fn();
    render(
      <ConfirmDialog open title="移出群聊" description="确定吗？" danger onConfirm={onConfirm} onClose={onClose} />,
    );

    fireEvent.click(screen.getByRole('button', { name: '确定' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: '取消' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
