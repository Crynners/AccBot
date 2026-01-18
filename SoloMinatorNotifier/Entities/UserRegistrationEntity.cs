using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace SoloMinatorNotifier.Entities;

[Table("UserRegistrations", Schema = "solo")]
public class UserRegistrationEntity
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    public int Id { get; set; }

    [Required]
    [MaxLength(100)]
    public string MiningAddress { get; set; } = string.Empty;

    [Required]
    [MaxLength(50)]
    public string PoolVariant { get; set; } = "solo"; // solo, eusolo, ausolo

    [MaxLength(50)]
    public string? TelegramChatId { get; set; }

    public bool NotificationsEnabled { get; set; } = false;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    [NotMapped]
    public string PoolBaseUrl => PoolVariant switch
    {
        "eusolo" => "https://eusolo.ckpool.org",
        "ausolo" => "https://ausolo.ckpool.org",
        _ => "https://solo.ckpool.org"
    };

    [NotMapped]
    public string StatsApiUrl => $"{PoolBaseUrl}/users/{MiningAddress}";
}
