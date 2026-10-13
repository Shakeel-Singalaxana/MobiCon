using System.Diagnostics;
using System.Net.Http.Headers;
using System.Text.Json;

namespace PadLink;

public static class DriverSetup
{
    private const string ViGEmRepo = "nefarius/ViGEmBus";

    public static async Task<IReadOnlyList<DriverSetupResult>> InstallMissingAsync(DriverStatus status, CancellationToken ct)
    {
        var results = new List<DriverSetupResult>();
        if (!status.vigem.Installed)
        {
            results.Add(await DownloadAndLaunchAsync(
                "ViGEmBus",
                ViGEmRepo,
                asset => IsInstaller(asset) && asset.Contains("vigembus", StringComparison.OrdinalIgnoreCase),
                DriverProbe.ViGEmDownloadUrl,
                ct));
        }

        return results;
    }

    public static async Task<DriverSetupResult> InstallDriverAsync(string driver, CancellationToken ct)
    {
        return driver.Trim().ToLowerInvariant() switch
        {
            "vigem" or "vgembus" or "vigembus driver" => await DownloadAndLaunchAsync(
                "ViGEmBus",
                ViGEmRepo,
                asset => IsInstaller(asset) && asset.Contains("vigembus", StringComparison.OrdinalIgnoreCase),
                DriverProbe.ViGEmDownloadUrl,
                ct),
            _ => new DriverSetupResult(false, $"Unknown driver '{driver}'.")
        };
    }

    private static bool IsInstaller(string assetName)
    {
        return assetName.EndsWith(".exe", StringComparison.OrdinalIgnoreCase)
            || assetName.EndsWith(".msi", StringComparison.OrdinalIgnoreCase);
    }

    private static async Task<DriverSetupResult> DownloadAndLaunchAsync(
        string driverName,
        string repo,
        Func<string, bool> assetPredicate,
        string fallbackUrl,
        CancellationToken ct)
    {
        try
        {
            var asset = await ResolveLatestAssetAsync(repo, assetPredicate, ct);
            if (asset == null)
            {
                OpenUrl(fallbackUrl);
                return new DriverSetupResult(false, $"Could not find a downloadable {driverName} installer asset. Opened release page instead.");
            }

            string dir = Path.Combine(AppContext.BaseDirectory, "drivers");
            Directory.CreateDirectory(dir);
            string path = Path.Combine(dir, SanitizeFileName(asset.Value.Name));

            if (!File.Exists(path) || new FileInfo(path).Length == 0)
            {
                using var http = CreateHttpClient();
                await using var stream = await http.GetStreamAsync(asset.Value.Url, ct);
                await using var file = File.Create(path);
                await stream.CopyToAsync(file, ct);
            }

            LaunchInstaller(path);
            return new DriverSetupResult(true, $"Downloaded and launched {driverName} installer.", path);
        }
        catch (Exception ex)
        {
            return new DriverSetupResult(false, $"Could not install {driverName}: {ex.Message}");
        }
    }

    private static async Task<(string Name, string Url)?> ResolveLatestAssetAsync(string repo, Func<string, bool> assetPredicate, CancellationToken ct)
    {
        using var http = CreateHttpClient();
        await using var stream = await http.GetStreamAsync($"https://api.github.com/repos/{repo}/releases/latest", ct);
        using var doc = await JsonDocument.ParseAsync(stream, cancellationToken: ct);
        if (!doc.RootElement.TryGetProperty("assets", out var assets)) return null;

        foreach (var asset in assets.EnumerateArray())
        {
            string name = asset.TryGetProperty("name", out var nameProp) ? nameProp.GetString() ?? "" : "";
            string url = asset.TryGetProperty("browser_download_url", out var urlProp) ? urlProp.GetString() ?? "" : "";
            if (name.Length > 0 && url.Length > 0 && assetPredicate(name))
            {
                return (name, url);
            }
        }

        foreach (var asset in assets.EnumerateArray())
        {
            string name = asset.TryGetProperty("name", out var nameProp) ? nameProp.GetString() ?? "" : "";
            string url = asset.TryGetProperty("browser_download_url", out var urlProp) ? urlProp.GetString() ?? "" : "";
            if (name.Length > 0 && url.Length > 0 && IsInstaller(name))
            {
                return (name, url);
            }
        }

        return null;
    }

    private static HttpClient CreateHttpClient()
    {
        var http = new HttpClient();
        http.DefaultRequestHeaders.UserAgent.Add(new ProductInfoHeaderValue("mobicon-receiver", "1.0"));
        http.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/vnd.github+json"));
        return http;
    }

    private static void LaunchInstaller(string path)
    {
        ProcessStartInfo psi;
        if (path.EndsWith(".msi", StringComparison.OrdinalIgnoreCase))
        {
            psi = new ProcessStartInfo
            {
                FileName = "msiexec.exe",
                Arguments = $"/i \"{path}\"",
                UseShellExecute = true,
                Verb = "runas"
            };
        }
        else
        {
            psi = new ProcessStartInfo
            {
                FileName = path,
                UseShellExecute = true,
                Verb = "runas",
                WorkingDirectory = Path.GetDirectoryName(path)
            };
        }

        Process.Start(psi);
    }

    private static void OpenUrl(string url)
    {
        Process.Start(new ProcessStartInfo { FileName = url, UseShellExecute = true });
    }

    private static string SanitizeFileName(string fileName)
    {
        foreach (char c in Path.GetInvalidFileNameChars())
        {
            fileName = fileName.Replace(c, '_');
        }
        return fileName;
    }

}
