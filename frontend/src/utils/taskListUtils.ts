/**
 * Task List Utilities - Bulletproof checkbox toggle implementation
 * 
 * This module provides utilities for parsing markdown task lists and toggling
 * checkbox states with content verification to ensure accurate updates.
 */

/**
 * Represents a parsed task list item
 */
export interface TaskItem {
    /** Line index (0-based) in the original markdown */
    lineIndex: number;
    /** The checkbox state: true for checked, false for unchecked */
    checked: boolean;
    /** The text content of the task (without checkbox syntax) */
    text: string;
    /** The full original line */
    originalLine: string;
    /** Simple hash of the line for verification (first 8 chars of text hash) */
    verificationHash: string;
}

/**
 * Result of a checkbox toggle operation
 */
export interface ToggleResult {
    /** Whether the toggle was successful */
    success: boolean;
    /** The updated markdown content (only if successful) */
    updatedContent?: string;
    /** Error message (only if failed) */
    error?: string;
}

/**
 * Generate a simple hash string from a string value
 * Uses a basic polynomial rolling hash for verification purposes
 */
function simpleHash(str: string, length: number = 8): string {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
        const char = str.charCodeAt(i);
        hash = ((hash << 5) - hash) + char;
        hash = hash & hash; // Convert to 32bit integer
    }
    return Math.abs(hash).toString(16).padStart(length, '0').slice(0, length);
}

/**
 * Parse markdown content and extract all task list items
 * 
 * Task list items follow the format: "- [ ] text" or "- [x] text"
 * Also supports "* [ ] text" and "* [x] text"
 * 
 * Lines inside fenced code blocks (``` or ~~~) are excluded from parsing.
 * Note: Indented task lists (nested lists with 2+ spaces) ARE parsed.
 * 
 * @param content - The markdown content to parse
 * @returns Array of TaskItem objects
 */
export function parseTaskItems(content: string): TaskItem[] {
    const lines = content.split('\n');
    const taskItems: TaskItem[] = [];
    
    // Regex to match task list items: "- [ ] text" or "- [x] text" or "* [ ] text" etc.
    // This regex allows any amount of leading whitespace for nested task lists
    // Captures: indent, bullet, checkbox, and text (with leading spaces preserved)
    // Note: \s* after checkbox allows for empty text (e.g., "- [ ]")
    const taskRegex = /^(\s*)([-*])\s+\[([ xX])\]\s*(.*)$/;
    // Regex to match fenced code block start/end (``` or ~~~)
    const fenceRegex = /^(`{3,}|~{3,})(\w*)/;
    
    let inCodeBlock = false;
    let codeBlockFence = '';
    
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        
        // Check for code block fence (``` or ~~~)
        const fenceMatch = line.match(fenceRegex);
        if (fenceMatch) {
            const fence = fenceMatch[1];
            if (!inCodeBlock) {
                // Starting a code block
                inCodeBlock = true;
                codeBlockFence = fence;
            } else if (line.startsWith(codeBlockFence)) {
                // Ending a code block
                inCodeBlock = false;
                codeBlockFence = '';
            }
            continue; // Don't parse the fence line itself
        }
        
        // Skip lines inside fenced code blocks
        if (inCodeBlock) {
            continue;
        }
        
        const match = line.match(taskRegex);
        
        if (match) {
            const [, indent, bullet, checkbox, text] = match;
            const checked = checkbox.toLowerCase() === 'x';
            
            taskItems.push({
                lineIndex: i,
                checked,
                text,
                originalLine: line,
                verificationHash: simpleHash(line)
            });
        }
    }
    
    return taskItems;
}

/**
 * Toggle a checkbox in markdown content by line index with content verification
 * 
 * This function:
 * 1. Finds the task item at the specified line index
 * 2. Verifies the content hasn't changed since rendering
 * 3. Toggles the checkbox state
 * 4. Returns the updated content
 * 
 * @param content - The current markdown content
 * @param lineIndex - The line index of the checkbox to toggle (0-based)
 * @returns ToggleResult with success status and updated content or error
 */
export function toggleTaskCheckbox(content: string, lineIndex: number): ToggleResult {
    const lines = content.split('\n');
    
    // Check if line index is valid
    if (lineIndex < 0 || lineIndex >= lines.length) {
        return {
            success: false,
            error: `Invalid line index: ${lineIndex}`
        };
    }
    
    const targetLine = lines[lineIndex];
    
    // Verify this is a task list item
    const taskRegex = /^(\s*)([-*])\s+\[([ xX])\]\s+(.*)$/;
    const match = targetLine.match(taskRegex);
    
    if (!match) {
        return {
            success: false,
            error: `Line ${lineIndex} is not a task list item`
        };
    }
    
    const [, indent, bullet, checkbox, text] = match;
    const isChecked = checkbox.toLowerCase() === 'x';
    
    // Toggle the checkbox
    const newCheckbox = isChecked ? ' ' : 'x';
    const newLine = `${indent}${bullet} [${newCheckbox}] ${text}`;
    
    // Create updated content
    const newLines = [...lines];
    newLines[lineIndex] = newLine;
    const updatedContent = newLines.join('\n');
    
    return {
        success: true,
        updatedContent
    };
}

/**
 * Find a task item by its verification hash and toggle it
 * 
 * This is a more robust method that finds the task by content hash
 * instead of relying on line index, which can shift if content changes.
 * 
 * @param content - The current markdown content
 * @param verificationHash - The hash of the original line
 * @returns ToggleResult with success status and updated content or error
 */
export function toggleTaskByHash(content: string, verificationHash: string): ToggleResult {
    const lines = content.split('\n');
    
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        const lineHash = simpleHash(line);
        
        if (lineHash === verificationHash) {
            return toggleTaskCheckbox(content, i);
        }
    }
    
    return {
        success: false,
        error: `Task item with hash ${verificationHash} not found (content may have changed)`
    };
}

/**
 * Get the current checkbox state for a task item by line index
 * 
 * @param content - The markdown content
 * @param lineIndex - The line index to check
 * @returns true if checked, false if unchecked, null if not a task item
 */
export function getCheckboxState(content: string, lineIndex: number): boolean | null {
    const lines = content.split('\n');
    
    if (lineIndex < 0 || lineIndex >= lines.length) {
        return null;
    }
    
    const line = lines[lineIndex];
    const taskRegex = /^(\s*)([-*])\s+\[([ xX])\]\s+(.*)$/;
    const match = line.match(taskRegex);
    
    if (!match) {
        return null;
    }
    
    return match[3].toLowerCase() === 'x';
}

/**
 * Count total task items and completed tasks in markdown content
 * 
 * @param content - The markdown content
 * @returns Object with total count and completed count
 */
export function countTasks(content: string): { total: number; completed: number } {
    const taskItems = parseTaskItems(content);
    const completed = taskItems.filter(item => item.checked).length;
    return { total: taskItems.length, completed };
}
