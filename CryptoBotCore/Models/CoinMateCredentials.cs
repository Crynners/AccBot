using Microsoft.Extensions.Configuration;
using System;
using System.Collections.Generic;
using System.Text;

namespace CryptoBotCore.Models
{
    public class CoinMateCredentials
    {
        public int ClientId { get; set; }
        public string PublicKey { get; set; }
        public string PrivateKey { get; set; }
    }
}
