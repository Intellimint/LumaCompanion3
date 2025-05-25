using System.Threading.Tasks;
using LumaCompanion.WinUI.Models;

namespace LumaCompanion.WinUI.Services
{
    public interface ISettingsService
    {
        Task<PersonalizationSettings> LoadPersonalizationAsync();
        Task SavePersonalizationAsync(PersonalizationSettings settings);
    }
}
