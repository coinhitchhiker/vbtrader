
import subprocess
import sys
import os

java_bin = os.environ['JAVA_HOME'] + "/bin"

cmd = [java_bin + "/java", "-Xmx384m",  "-jar", "simulator.jar"
      , "--simul-start" , "20171101"
      , "--simul-end" , "20171130"
      , "--symbol" , "BTCUSDT"
      , "--exchange" , "BINANCE"
      , "--mode" , "LONG"
      , "--quote-currency" , "USDT"
      , "--strategy" , "IBS"
      ,"--bb-input" , sys.argv[1]]

run_result = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
stdout, strerr = run_result.communicate()

print (stdout.decode('ascii').strip())
