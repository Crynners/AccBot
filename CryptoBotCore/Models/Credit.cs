using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace CryptoBotCore.Models
{
    [Serializable]
    public class Credit
    {
        public int id { get; set; }
        public string currency { get; set; }
        public string status { get; set; }
        public string rate { get; set; }
        public int period { get; set; }
        public double amount { get; set; }
        public string timestamp { get; set; }
    }
}
