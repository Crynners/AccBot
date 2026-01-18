using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace SoloMinator.Entities;

[Table("TelegramSubscriptions", Schema = "solo")]
public class TelegramSubscriptionEntity
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    public int Id { get; set; }

    public int UserRegistrationId { get; set; }

    [Required]
    [MaxLength(50)]
    public string TelegramChatId { get; set; } = string.Empty;

    [MaxLength(100)]
    public string? TelegramUsername { get; set; }

    [MaxLength(100)]
    public string? TelegramFirstName { get; set; }

    public bool IsActive { get; set; } = true;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public DateTime? LastNotificationAt { get; set; }

    [ForeignKey(nameof(UserRegistrationId))]
    public virtual UserRegistrationEntity? UserRegistration { get; set; }
}
