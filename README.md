Приложение для сохранения истории, об уровне сигнала сим-карты и типе передачи данных (интернета). Приложение сохраняет полученные данные (по минутам) и рисует из них суточный график.

 С его помощью можно сравнить как ведут себя разные симки в равных условиях. Или просто узнать когда, какой сигнал был в течении дня. 
Приложение не является инструментом измерения.
Все данные о работе сим-карт хранятся в вашем телефоне в папке приложения в текстовых файлах. И никуда не передаются.

Данные с которыми работает приложение:
1. Уровень сигнала мобильной сети.
2. Тип передачи данных.
3. Номер слота активной SIM-карты.
4. Название SIM-карты.

Для получения таких данных от телефона, приложение потребует разрешение на управление звонками.
Приложение работает как фоновый сервис, поэтому для длительной и стабильной работы ему требуется разрешение на отображение постоянного уведомления и рекомендуется снятие ограничения контроля активности (батарейка).

С помощью приложения вы так же можете отобразить график уровня сигнала и типа интернета вашего роутера Mikrotik с LTE модемом, следуя следующей инструкции.

Скрипт проверен на роутере hAP AC3 LTE6 RouterOs 7.20.4 , прошивка модема R11e-LTE6_V039
У вас в роутере должен быть настроен инструмент email (tools/email)

1. добавьте планировщик
 /system/scheduler/add name=simlogger start-time=startup interval=00:00:55 on-event=simlogger
2. создайте скрипт с названием simlogger и добавьте в скрипт следующий код, не забудьте указать свою почту в скрипте.
```bash
:local toEmail "you@email.net"
:local MAXMINUTE 1438
:local id [/system/identity/get name]

:local lteData [/interface/lte print as-value]
:local ifaceName ""
:if ([:len $lteData] > 0) do={
    :set ifaceName ([:pick $lteData 0]->"name")
} else={
    :return
}

:global LTELog
:if ([:len $LTELog] = 0) do={ :set LTELog "" }

:local t [/system/clock/get time]
:local hh [:tonum [:pick $t 0 2]]
:local mi [:tonum [:pick $t 3 5]]
:local minute (($hh * 60) + $mi)

:local mon [/interface/lte monitor $ifaceName once as-value]

:local techRaw ""
:if ([:len ($mon->"access-technology")] > 0) do={
    :set techRaw ($mon->"access-technology")
}

:local rssiStr ""
:if ([:len ($mon->"signal-strength")] > 0) do={
    :set rssiStr ($mon->"signal-strength")
} else={
    :if ([:len ($mon->"rssi")] > 0) do={
        :set rssiStr ($mon->"rssi")
    }
}

:local rssi 0
:if ([:len $rssiStr] > 0) do={
    :local L [:len $rssiStr]
    :if ($L > 3 && [:pick $rssiStr ($L-3) $L] = "dBm") do={
        :set rssi [:tonum [:pick $rssiStr 0 ($L-3)]]
    } else={
        :set rssi [:tonum $rssiStr]
    }
}

:local tech "xG"
:if ($techRaw = "LTE" || $techRaw = "E-UTRAN" || $techRaw = "3") do={
    :set tech "4G"
} else={
    :if ($techRaw = "NR5G") do={
        :set tech "5G"
    } else={
        :if ($techRaw = "WCDMA" || $techRaw = "UTRAN" || $techRaw = "1" || $techRaw = "2") do={
            :set tech "3G"
        } else={
            :if ($techRaw = "GPRS" || $techRaw = "EDGE" || $techRaw = "GSM" || $techRaw = "0") do={
                :set tech "2G"
            }
        }
    }
}

:local lvl 0
:if ($rssi > -75) do={:set lvl 4} else={
    :if ($rssi > -85) do={:set lvl 3} else={
        :if ($rssi > -95) do={:set lvl 2} else={
            :if ($rssi > -105) do={:set lvl 1} else={:set lvl 0}
        }
    }
}

#/log info ("LTE dbg: iface=" . $ifaceName . " techRaw=" . $techRaw . " tech=" . $tech . " rssiStr=" . $rssiStr . " rssi=" . $rssi . " lvl=" . $lvl)

:global LTELog
:global LTEFileName
:local line ($minute . "," . $tech . "," . $lvl)

:if ($minute < $MAXMINUTE) do={
    :if ($LTELog = "") do={
        :set LTELog $line
    } else={
        :set LTELog ($LTELog . "\r\n" . $line)
    }
} else={
    :if ([:len $LTELog] > 0) do={
        :local dateStr [/system/clock/get date]
        :set LTEFileName ($id . "_" . $dateStr . ".txt")

        /file print file=$LTEFileName
        /file set $LTEFileName contents=$LTELog

        /tool e-mail send to=$toEmail subject=("LTE Log for " . $dateStr . " (" . $id . ")") file=$LTEFileName
        :set LTELog ""
    } else={
        :if ([/file find name=$LTEFileName] != "") do={
         /file remove $LTEFileName
         }
    }
}
```
этот скрипт будет каждые 55 секунд запрашивать уровень сигнала и тип подключения у модеме и сохранять в глобальную переменную, 
в 23:58 он будет отправлять письмо на указанную вами почту с файлом с наокпленной за сутки статистикой, в 23:59 он будет удалять этот файл из роутера после отправки.
Имя файла будет состоять из Identity роутера и текущей даты, этот файл вы можете положить в телефон,
в папку приложения (/Android/data/com.safelogj.simlog/files/) и открыть в приложении.


Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
