import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';

describe('基础组件冒烟测试', () => {
  it('1 + 1 = 2', () => {
    expect(1 + 1).toBe(2);
  });

  it('能够 mount 一个简单的 div', () => {
    const wrapper = mount({
      template: '<div>Hello Clawhelm</div>',
    });
    expect(wrapper.text()).toBe('Hello Clawhelm');
  });
});
