# Magic Code Insert

A JetBrains IDE plugin that uses LLM (Large Language Models) to intelligently insert code at your cursor position.

## Features

- 🚀 **Hotkey-driven code insertion**: Press `Ctrl+Alt+L` to send your code to an LLM and get intelligent completions
- 🔧 **OpenAI-compatible API support**: Works with OpenAI, Azure OpenAI, and any OpenAI-compatible endpoints
- ⚙️ **Fully configurable**: Customize API endpoint, model, temperature, and system prompt
- 📍 **Cursor marker**: The plugin automatically inserts a `<<<CURSOR>>>` marker at your cursor position
- 🔒 **Secure**: API keys are stored securely in IDE settings

## Installation

### From Source

1. Clone this repository
2. Build the plugin:
   ```bash
   ./gradlew buildPlugin
   ```
3. Install the plugin from disk in your JetBrains IDE:
   - Go to `Settings/Preferences` → `Plugins` → ⚙️ → `Install Plugin from Disk`
   - Select the built plugin from `build/distributions/MagicCodeInsert-*.zip`

## Configuration

1. Go to `Settings/Preferences` → `Tools` → `Magic Code Insert`
2. Configure the following settings:

### Required Settings

- **API Endpoint**: The OpenAI-compatible API endpoint (default: `https://api.openai.com/v1/chat/completions`)
- **API Key**: Your API key for authentication
- **Model**: The model to use (e.g., `gpt-4`, `gpt-3.5-turbo`)

### Optional Settings

- **Temperature**: Controls randomness (0.0 - 2.0, default: 0.7)
- **Max Tokens**: Maximum tokens in the response (default: 2000)
- **System Prompt**: Instructions for the LLM (includes `<<<CURSOR>>>` marker explanation)

## Usage

1. Open any code file in your JetBrains IDE
2. Place your cursor where you want code to be inserted
3. Press `Ctrl+Alt+L` (or `Cmd+Alt+L` on macOS)
4. Wait for the LLM to generate code
5. The generated code will be automatically inserted at your cursor position

## How It Works

1. When you trigger the action, the plugin:
   - Captures the entire current file content
   - Inserts a `<<<CURSOR>>>` marker at your cursor position
   - Sends the code with marker to the configured LLM API
   
2. The LLM receives:
   - System prompt with instructions
   - Your code with the `<<<CURSOR>>>` marker indicating where to insert new code
   
3. The LLM returns code to insert, which is then placed at your cursor position

## Example

**Before** (cursor at `|`):
```javascript
function calculateTotal() {
    const items = getItems();
    |
}
```

**What the LLM sees**:
```javascript
function calculateTotal() {
    const items = getItems();
    <<<CURSOR>>>
}
```

**After** (LLM generates and inserts):
```javascript
function calculateTotal() {
    const items = getItems();
    const total = items.reduce((sum, item) => sum + item.price, 0);
    return total;
}
```

## Testing

The plugin includes comprehensive unit tests. Run them with:

```bash
./gradlew test
```

Test coverage includes:
- ✅ LLM API communication with mock server
- ✅ Settings persistence and validation
- ✅ Cursor marker insertion logic
- ✅ Error handling for API failures
- ✅ Request/response formatting

## Development

### Prerequisites

- JDK 21 or higher
- Gradle 9.0+
- IntelliJ IDEA (recommended)

### Building

```bash
./gradlew build
```

### Running in Development

```bash
./gradlew runIde
```

## Architecture

### Components

1. **MagicCodeInsertSettings**: Persistent settings storage
2. **MagicCodeInsertConfigurable**: Settings UI
3. **LLMService**: HTTP client for API communication
4. **InsertCodeFromLLMAction**: Editor action with hotkey binding

### Technologies

- **Kotlin**: Primary language
- **IntelliJ Platform SDK**: IDE integration
- **OkHttp**: HTTP client
- **Gson**: JSON serialization
- **JUnit 5**: Testing framework
- **MockWebServer**: API mocking

## Customization

### Custom System Prompt

You can customize the system prompt to change how the LLM behaves. For example:

```
You are a senior software engineer. 
When you see code with a <<<CURSOR>>> marker:
1. Analyze the context
2. Generate only the missing implementation
3. Follow the project's coding style
4. Add helpful comments
Do not include explanations or markdown.
```

### Alternative API Endpoints

The plugin works with any OpenAI-compatible API:

- **OpenAI**: `https://api.openai.com/v1/chat/completions`
- **Azure OpenAI**: `https://<your-resource>.openai.azure.com/openai/deployments/<deployment>/chat/completions?api-version=2024-02-15-preview`
- **Local LLMs** (like Ollama): `http://localhost:11434/v1/chat/completions`

## Troubleshooting

### "API key is not configured"
- Go to Settings and enter your API key

### "API request failed: 401"
- Check that your API key is correct
- Verify the API endpoint is correct

### "No completion in response"
- The API returned an empty response
- Try increasing Max Tokens
- Check your system prompt

### Tests failing
- Ensure you're using JDK 21+
- Run with: `JAVA_HOME=/path/to/jdk21 ./gradlew test`

## License

This project is for educational and personal use.

## Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues.

## Changelog

### Version 1.0-SNAPSHOT
- Initial release
- OpenAI-compatible API support
- Configurable settings
- Hotkey support (`Ctrl+Alt+L`)
- Comprehensive test suite
