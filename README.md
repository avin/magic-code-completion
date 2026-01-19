# Magic Code Insert

JetBrains IDEs plugin for AI-assisted code completion using OpenAI-compatible APIs with agentic file reading and multi-edit capabilities.

## Features

- **Alt+I hotkey** - Quick code completion at cursor position
- **Agentic file access** - LLM can dynamically request and read project files
- **Multi-edit support** - Apply multiple code changes in a single request
- **Project-aware** - File tree context

## Settings

**Tools → Magic Code Insert**

### API Configuration (Global)
- API Endpoint (default: OpenAI)
- API Key
- Model name
- System prompt
- Temperature, max tokens
- Timeouts
- Debug mode

### Project Files (Project-specific)
- Include patterns - glob patterns for files to include in context
- Exclude patterns - regex patterns for test files and other exclusions

## How it works

1. Press **Alt+I** in any editor at the point where you want to insert a completion
2. Plugin sends current file with cursor position marker + file tree to LLM
3. LLM can request to read additional project files or apply code changes
4. Changes are applied

