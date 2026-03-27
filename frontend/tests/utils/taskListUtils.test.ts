import { describe, test, expect } from '@jest/globals';
import {
    parseTaskItems,
    toggleTaskCheckbox,
    toggleTaskByHash,
    getCheckboxState,
    countTasks,
    TaskItem
} from '../../src/utils/taskListUtils';

describe('taskListUtils', () => {
    describe('parseTaskItems', () => {
        test('should parse simple unchecked task', () => {
            const content = '- [ ] todo item';
            const result = parseTaskItems(content);
            
            expect(result).toHaveLength(1);
            expect(result[0]).toMatchObject({
                lineIndex: 0,
                checked: false,
                text: 'todo item',
                originalLine: '- [ ] todo item'
            });
        });

        test('should parse simple checked task', () => {
            const content = '- [x] completed item';
            const result = parseTaskItems(content);
            
            expect(result).toHaveLength(1);
            expect(result[0]).toMatchObject({
                lineIndex: 0,
                checked: true,
                text: 'completed item',
                originalLine: '- [x] completed item'
            });
        });

        test('should parse multiple tasks', () => {
            const content = `- [ ] first task
- [x] second task
- [ ] third task`;
            const result = parseTaskItems(content);
            
            expect(result).toHaveLength(3);
            expect(result[0].checked).toBe(false);
            expect(result[1].checked).toBe(true);
            expect(result[2].checked).toBe(false);
        });

        test('should parse tasks with asterisk bullet', () => {
            const content = '* [ ] asterisk task';
            const result = parseTaskItems(content);
            
            expect(result).toHaveLength(1);
            expect(result[0]).toMatchObject({
                lineIndex: 0,
                checked: false,
                text: 'asterisk task'
            });
        });

        test('should parse tasks with indentation', () => {
            const content = '    - [ ] indented task';
            const result = parseTaskItems(content);
            
            expect(result).toHaveLength(1);
            expect(result[0]).toMatchObject({
                lineIndex: 0,
                checked: false,
                text: 'indented task',
                originalLine: '    - [ ] indented task'
            });
        });

        test('should parse tasks with uppercase X', () => {
            const content = '- [X] uppercase X task';
            const result = parseTaskItems(content);
            
            expect(result).toHaveLength(1);
            expect(result[0].checked).toBe(true);
        });

        test('should ignore non-task lines', () => {
            const content = `- Regular bullet
- [ ] actual task
Plain text
- [x] another task`;
            const result = parseTaskItems(content);
            
            expect(result).toHaveLength(2);
            expect(result[0].lineIndex).toBe(1);
            expect(result[1].lineIndex).toBe(3);
        });

        test('should handle tasks with special characters in text', () => {
            const content = '- [ ] task with "quotes" and \'apostrophes\'';
            const result = parseTaskItems(content);
            
            expect(result).toHaveLength(1);
            expect(result[0].text).toBe('task with "quotes" and \'apostrophes\'');
        });

        test('should handle empty task text', () => {
            const content = '- [ ]';
            const result = parseTaskItems(content);
            
            expect(result).toHaveLength(1);
            expect(result[0].text).toBe('');
        });

        test('should handle duplicate task texts', () => {
            const content = `- [ ] duplicate
- [x] duplicate
- [ ] duplicate`;
            const result = parseTaskItems(content);
            
            expect(result).toHaveLength(3);
            expect(result[0].lineIndex).toBe(0);
            expect(result[1].lineIndex).toBe(1);
            expect(result[2].lineIndex).toBe(2);
            expect(result[0].text).toBe('duplicate');
            expect(result[1].text).toBe('duplicate');
            expect(result[2].text).toBe('duplicate');
        });

        test('should handle nested task lists', () => {
            const content = `- [ ] parent task
  - [x] child task 1
  - [ ] child task 2`;
            const result = parseTaskItems(content);
            
            expect(result).toHaveLength(3);
            expect(result[0].lineIndex).toBe(0);
            expect(result[1].lineIndex).toBe(1);
            expect(result[2].lineIndex).toBe(2);
        });

        test('should handle mixed content with headers and tasks', () => {
            const content = `# Heading

Some text

- [ ] task after header

## Another heading

- [x] task after second heading`;
            const result = parseTaskItems(content);
            
            expect(result).toHaveLength(2);
            expect(result[0].lineIndex).toBe(4);
            expect(result[1].lineIndex).toBe(8);
        });
    });

    describe('toggleTaskCheckbox', () => {
        test('should toggle unchecked to checked', () => {
            const content = '- [ ] todo item';
            const result = toggleTaskCheckbox(content, 0);
            
            expect(result.success).toBe(true);
            expect(result.updatedContent).toBe('- [x] todo item');
        });

        test('should toggle checked to unchecked', () => {
            const content = '- [x] completed item';
            const result = toggleTaskCheckbox(content, 0);
            
            expect(result.success).toBe(true);
            expect(result.updatedContent).toBe('- [ ] completed item');
        });

        test('should toggle in multi-line content', () => {
            const content = `- [ ] first task
- [x] second task
- [ ] third task`;
            const result = toggleTaskCheckbox(content, 1);
            
            expect(result.success).toBe(true);
            expect(result.updatedContent).toBe(`- [ ] first task
- [ ] second task
- [ ] third task`);
        });

        test('should fail on invalid line index', () => {
            const content = '- [ ] todo';
            const result = toggleTaskCheckbox(content, 10);
            
            expect(result.success).toBe(false);
            expect(result.error).toContain('Invalid line index');
        });

        test('should fail on non-task line', () => {
            const content = 'not a task line';
            const result = toggleTaskCheckbox(content, 0);
            
            expect(result.success).toBe(false);
            expect(result.error).toContain('not a task list item');
        });

        test('should preserve indentation when toggling', () => {
            const content = '    - [ ] indented task';
            const result = toggleTaskCheckbox(content, 0);
            
            expect(result.success).toBe(true);
            expect(result.updatedContent).toBe('    - [x] indented task');
        });

        test('should preserve text content when toggling', () => {
            const content = '- [ ] task with special chars: @#$%^&*()';
            const result = toggleTaskCheckbox(content, 0);
            
            expect(result.success).toBe(true);
            expect(result.updatedContent).toBe('- [x] task with special chars: @#$%^&*()');
        });
    });

    describe('toggleTaskByHash', () => {
        test('should toggle task by verification hash', () => {
            const content = '- [ ] todo item';
            const taskItems = parseTaskItems(content);
            const hash = taskItems[0].verificationHash;
            
            const result = toggleTaskByHash(content, hash);
            
            expect(result.success).toBe(true);
            expect(result.updatedContent).toBe('- [x] todo item');
        });

        test('should fail if hash not found', () => {
            const content = '- [ ] todo item';
            const result = toggleTaskByHash(content, 'invalidhash123');
            
            expect(result.success).toBe(false);
            expect(result.error).toContain('not found');
        });

        test('should find correct task among duplicates by hash', () => {
            const content = `- [ ] duplicate
- [x] duplicate
- [ ] duplicate`;
            const taskItems = parseTaskItems(content);
            
            // Each duplicate has a unique hash based on the full line
            const result = toggleTaskByHash(content, taskItems[1].verificationHash);
            
            expect(result.success).toBe(true);
            // Should toggle the checked one to unchecked
            expect(result.updatedContent).toBe(`- [ ] duplicate
- [ ] duplicate
- [ ] duplicate`);
        });
    });

    describe('getCheckboxState', () => {
        test('should return true for checked task', () => {
            const content = '- [x] completed';
            const state = getCheckboxState(content, 0);
            expect(state).toBe(true);
        });

        test('should return false for unchecked task', () => {
            const content = '- [ ] todo';
            const state = getCheckboxState(content, 0);
            expect(state).toBe(false);
        });

        test('should return null for non-task line', () => {
            const content = 'not a task';
            const state = getCheckboxState(content, 0);
            expect(state).toBeNull();
        });

        test('should return null for invalid line index', () => {
            const content = '- [ ] todo';
            const state = getCheckboxState(content, 10);
            expect(state).toBeNull();
        });
    });

    describe('countTasks', () => {
        test('should count total and completed tasks', () => {
            const content = `- [ ] task 1
- [x] task 2
- [ ] task 3
- [x] task 4`;
            const result = countTasks(content);
            
            expect(result.total).toBe(4);
            expect(result.completed).toBe(2);
        });

        test('should return zeros for content with no tasks', () => {
            const content = '# No tasks here\nJust plain text';
            const result = countTasks(content);
            
            expect(result.total).toBe(0);
            expect(result.completed).toBe(0);
        });

        test('should handle empty content', () => {
            const result = countTasks('');
            
            expect(result.total).toBe(0);
            expect(result.completed).toBe(0);
        });
    });

    describe('Edge cases', () => {
        test('should handle very long task text', () => {
            const longText = 'a'.repeat(1000);
            const content = `- [ ] ${longText}`;
            const result = parseTaskItems(content);
            
            expect(result).toHaveLength(1);
            expect(result[0].text).toBe(longText);
        });

        test('should handle unicode characters', () => {
            const content = '- [ ] 日本語タスク 🎉';
            const result = parseTaskItems(content);
            
            expect(result).toHaveLength(1);
            expect(result[0].text).toBe('日本語タスク 🎉');
        });

        test('should handle tasks with markdown formatting in text', () => {
            const content = '- [ ] **bold** and *italic* text';
            const result = parseTaskItems(content);
            
            expect(result).toHaveLength(1);
            expect(result[0].text).toBe('**bold** and *italic* text');
        });

        test('should handle multiple spaces between checkbox and text', () => {
            const content = '- [ ]   task with extra spaces';
            const result = parseTaskItems(content);
            
            expect(result).toHaveLength(1);
            // The regex \s* consumes leading spaces after checkbox, so text starts without leading spaces
            expect(result[0].text).toBe('task with extra spaces');
        });

        test('should handle tabs as indentation', () => {
            const content = '\t- [ ] tab indented task';
            const result = parseTaskItems(content);
            
            expect(result).toHaveLength(1);
            expect(result[0].originalLine).toBe('\t- [ ] tab indented task');
        });

        test('should NOT parse task-like syntax inside code blocks', () => {
            // This is the critical edge case: task list syntax inside code blocks
            // should NOT be parsed as actual tasks
            const content = `- [x] 1
- [x] 2

testing

\`\`\`
- [ ] 1
- [ ] 2

\`\`\`

- [ ] 1
- [ ] 2`;
            const result = parseTaskItems(content);
            
            // Should only parse the 4 actual task items (2 checked at top, 2 unchecked at bottom)
            // NOT the 2 task-like items inside the code block
            expect(result).toHaveLength(4);
            
            // First two should be checked (line 0 and 1)
            expect(result[0].lineIndex).toBe(0);
            expect(result[0].checked).toBe(true);
            expect(result[1].lineIndex).toBe(1);
            expect(result[1].checked).toBe(true);
            
            // Last two should be unchecked (line 11 and 12)
            // Note: Line indices are 0-based, and the content has:
            // Line 0: - [x] 1
            // Line 1: - [x] 2
            // Line 2: (empty)
            // Line 3: testing
            // Line 4: (empty)
            // Line 5: ```
            // Line 6: - [ ] 1 (inside code block - skipped)
            // Line 7: - [ ] 2 (inside code block - skipped)
            // Line 8: (empty)
            // Line 9: ```
            // Line 10: (empty)
            // Line 11: - [ ] 1
            // Line 12: - [ ] 2
            expect(result[2].lineIndex).toBe(11);
            expect(result[2].checked).toBe(false);
            expect(result[3].lineIndex).toBe(12);
            expect(result[3].checked).toBe(false);
        });

        test('should NOT parse task-like syntax inside fenced code blocks with language', () => {
            const content = `- [ ] task 1
\`\`\`javascript
- [ ] this is code, not a task
- [x] also code
\`\`\`
- [x] task 2`;
            const result = parseTaskItems(content);
            
            // Should only parse the 2 actual tasks, not the 2 inside the code block
            expect(result).toHaveLength(2);
            expect(result[0].lineIndex).toBe(0);
            expect(result[0].checked).toBe(false);
            expect(result[1].lineIndex).toBe(5);
            expect(result[1].checked).toBe(true);
        });

        test('should handle indented task lists (nested lists with 4 spaces)', () => {
            const content = `- [ ] task 1

    - [ ] nested task
    - [x] another nested

- [x] task 2`;
            const result = parseTaskItems(content);
            
            // Nested task lists (4-space indentation) SHOULD be parsed
            // This is consistent with the test "should parse tasks with indentation"
            expect(result).toHaveLength(4);
            expect(result[0].lineIndex).toBe(0);
            expect(result[1].lineIndex).toBe(2);
            expect(result[2].lineIndex).toBe(3);
            expect(result[3].lineIndex).toBe(5);
        });
    });

    describe('toggleTaskCheckbox with code blocks', () => {
        test('should toggle correct task when content has code blocks with task-like syntax', () => {
            const content = `- [x] 1
- [x] 2

testing

\`\`\`
- [ ] 1
- [ ] 2

\`\`\`

- [ ] 1
- [ ] 2`;
            
            // Toggle the last task (line 12, index 3 in taskItems)
            const result = toggleTaskCheckbox(content, 12);
            
            expect(result.success).toBe(true);
            // The last task should now be checked
            const lines = result.updatedContent!.split('\n');
            expect(lines[12]).toBe('- [x] 2');
            // Code block should remain unchanged
            expect(lines[6]).toBe('- [ ] 1');
            expect(lines[7]).toBe('- [ ] 2');
        });

        test('should not modify code block content when toggling tasks', () => {
            const content = `- [ ] task 1
\`\`\`javascript
- [ ] code line 1
- [x] code line 2
\`\`\`
- [x] task 2`;
            
            // Toggle task 1 (line 0)
            const result = toggleTaskCheckbox(content, 0);
            
            expect(result.success).toBe(true);
            // Code block should remain unchanged
            expect(result.updatedContent).toContain(`\`\`\`javascript
- [ ] code line 1
- [x] code line 2
\`\`\``);
            // Task 1 should be checked now
            expect(result.updatedContent).toContain('- [x] task 1');
        });
    });
});
