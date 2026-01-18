using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace SoloMinator.Entities;

[Table("TelegramLinkTokens", Schema = "solo")]
public class TelegramLinkTokenEntity
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    public int Id { get; set; }

    public int UserRegistrationId { get; set; }

    [Required]
    [MaxLength(64)]
    public string Token { get; set; } = string.Empty;

    public DateTime ExpiresAt { get; set; }

    public bool IsUsed { get; set; } = false;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [ForeignKey(nameof(UserRegistrationId))]
    public virtual UserRegistrationEntity? UserRegistration { get; set; }
}
