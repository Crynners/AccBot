using System.Globalization;
using Microsoft.AspNetCore.Localization;

namespace SoloMinator.Middleware;

public class CultureMiddleware
{
    private readonly RequestDelegate _next;
    private static readonly string[] SupportedCultures = { "cs", "en" };
    private const string DefaultCulture = "cs";

    public CultureMiddleware(RequestDelegate next)
    {
        _next = next;
    }

    public async Task InvokeAsync(HttpContext context)
    {
        var path = context.Request.Path.Value ?? "";
        var segments = path.Split('/', StringSplitOptions.RemoveEmptyEntries);

        string culture = DefaultCulture;

        // Check if first segment is a culture code
        if (segments.Length > 0 && SupportedCultures.Contains(segments[0].ToLowerInvariant()))
        {
            culture = segments[0].ToLowerInvariant();
            // Rewrite path without culture prefix
            var newPath = "/" + string.Join("/", segments.Skip(1));
            if (string.IsNullOrEmpty(newPath) || newPath == "/")
            {
                newPath = "/Index";
            }
            context.Request.Path = newPath;
        }

        // Set culture using ASP.NET Core's proper mechanism
        var cultureInfo = new CultureInfo(culture);
        CultureInfo.CurrentCulture = cultureInfo;
        CultureInfo.CurrentUICulture = cultureInfo;

        // Set the request culture feature - this is what IStringLocalizer uses
        var requestCultureFeature = new RequestCultureFeature(
            new RequestCulture(cultureInfo, cultureInfo),
            new PathCultureProvider());
        context.Features.Set<IRequestCultureFeature>(requestCultureFeature);

        // Store culture in HttpContext for views
        context.Items["Culture"] = culture;

        await _next(context);
    }
}

/// <summary>
/// Dummy provider for RequestCultureFeature
/// </summary>
public class PathCultureProvider : IRequestCultureProvider
{
    public Task<ProviderCultureResult?> DetermineProviderCultureResult(HttpContext httpContext)
    {
        var culture = httpContext.Items["Culture"] as string ?? "cs";
        return Task.FromResult<ProviderCultureResult?>(new ProviderCultureResult(culture));
    }
}

public static class CultureMiddlewareExtensions
{
    public static IApplicationBuilder UseCultureMiddleware(this IApplicationBuilder builder)
    {
        return builder.UseMiddleware<CultureMiddleware>();
    }
}
