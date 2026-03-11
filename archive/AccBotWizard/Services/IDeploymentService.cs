using AccBotWizard.ViewModels;

namespace AccBotWizard.Services;

public interface IDeploymentService
{
    Task<bool> DeployAsync(WizardData data, Action<string> logCallback);
}
