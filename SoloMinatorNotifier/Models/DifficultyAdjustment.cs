using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;

namespace SoloMinatorNotifier.Models
{
    public class DifficultyAdjustment
    {
        public long BlockTimestamp { get; set; }
        public int BlockHeight { get; set; }
        public double Difficulty { get; set; }
        public double DifficultyChange { get; set; }

        public static List<DifficultyAdjustment> FromJson(string json)
        {
            var jsonArray = JsonSerializer.Deserialize<JsonElement[][]>(json);
            return jsonArray.Select(item => new DifficultyAdjustment
            {
                BlockTimestamp = item.ElementAt(0).GetInt64(),
                BlockHeight = item.ElementAt(1).GetInt32(),
                Difficulty = item.ElementAt(2).GetDouble(),
                DifficultyChange = item.ElementAt(3).GetDouble()
            }).ToList();
        }
    }
}
