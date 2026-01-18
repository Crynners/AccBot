using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace SoloMinatorNotifier.Entities
{
    [Table("MiningDifficultyAdjustment", Schema = "solo")]
    public class MiningDifficultyAdjustmentEntity
    {
        /// <summary>
        /// Výška bloku - primární klíč
        /// </summary>
        [Key]
        public int BlockHeight { get; set; }

        /// <summary>
        /// Unix timestamp bloku
        /// </summary>
        public long BlockTimestamp { get; set; }

        /// <summary>
        /// Obtížnost těžby
        /// </summary>
        public double Difficulty { get; set; }

        /// <summary>
        /// Změna obtížnosti oproti předchozímu stavu
        /// </summary>
        public double DifficultyChange { get; set; }

        /// <summary>
        /// Čas vytvoření bloku
        /// </summary>
        public DateTime BlockTime { get; set; }
    }
}
