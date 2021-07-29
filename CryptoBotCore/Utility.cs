using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace CryptoBotCore
{
    public static class Utility
    {
        public static Double timeDifference(Double dateFrom, Double dateTo)
        {
            return dateTo - dateFrom;
        }

        public static Double getUnixTimestamps(DateTime time)
        {
            return (time.Subtract(new DateTime(1970, 1, 1, 0, 0, 0, 0, System.DateTimeKind.Utc))).TotalSeconds;
        }

        public static DateTime getDateTimeFromUnixTimestamp(Double unixTimeStamp)
        {
            // Unix timestamp is seconds past epoch
            DateTime dtDateTime = new DateTime(1970, 1, 1, 0, 0, 0, 0, System.DateTimeKind.Utc);
            if(unixTimeStamp > 1000000000000)
            dtDateTime = dtDateTime.AddMilliseconds(unixTimeStamp).ToLocalTime();
            else
                dtDateTime = dtDateTime.AddSeconds(unixTimeStamp).ToLocalTime();
            return dtDateTime;
        }

        public static DateTime StartOfWeek(this DateTime dt, DayOfWeek startOfWeek = DayOfWeek.Monday)
        {
            int diff = (7 + (dt.DayOfWeek - startOfWeek)) % 7;
            return dt.AddDays(-1 * diff).Date;
        }
    }
}
