
import subprocess
import sys
import os

java_bin = os.environ['JAVA_HOME'] + "/bin"

cmd = [java_bin + "/java", "-Xmx384m",  "-jar", "simulator.jar"
      , "--simul-start" , "20190824"
      , "--simul-end" , "20190826"
      , "--symbol" , "BTCUSDT"
      , "--exchange" , "BINANCE"
      , "--mode" , "LONG"
      , "--quote-currency" , "USDT"
      , "--strategy" , "VB"
      ,"--bb-input" , sys.argv[1]]

run_result = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
stdout, strerr = run_result.communicate()

print (stdout.decode('ascii').strip())
# print (stdout.decode('ascii').split('\n')[-2])
