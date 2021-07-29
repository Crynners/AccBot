using System;
using System.Collections;
using System.Collections.Generic;
using System.Text;

namespace CryptoBotCore.Models
{
    public class GenericRequest
    {
        public string request;
        public string nonce;
        public ArrayList options = new ArrayList();
    }
}
