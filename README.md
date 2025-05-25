## API Key Management (Development)

For local development, API keys should be stored using .NET User Secrets to avoid committing them to source control.

1.  Initialize User Secrets for the project:
    ```bash
    dotnet user-secrets init --project .
    ```
2.  Set the secrets. For example:
    ```bash
    dotnet user-secrets set "ApiKeys:OpenAI" "your_openai_key"
    dotnet user-secrets set "ApiKeys:DeepSeek" "your_deepseek_key"
    dotnet user-secrets set "ApiKeys:ElevenLabs" "your_elevenlabs_key"
    dotnet user-secrets set "TTSProvider" "OpenAI" 
    ```
3.  These can then be loaded via IConfiguration in the application. The `secrets.json` file would look something like this (managed by the secrets tool):
    ```json
    {
      "ApiKeys": {
        "OpenAI": "your_openai_key",
        "DeepSeek": "your_deepseek_key",
        "ElevenLabs": "your_elevenlabs_key"
      },
      "TTSProvider": "OpenAI" 
    }
    ```
