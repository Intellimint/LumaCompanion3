using System.Collections.Generic;
using System.Threading.Tasks;
using LumaCompanion.WinUI.Models;

namespace LumaCompanion.WinUI.Services
{
    public interface IConversationLogService
    {
        Task SaveEntryAsync(LogEntry entry);
        Task<List<LogEntry>> LoadEntriesAsync();
    }
}
