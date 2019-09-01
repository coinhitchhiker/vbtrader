
import subprocess
import sys
import os

java_bin = os.environ['JAVA_HOME'] + "/bin"

cmd = [java_bin + "/java", "-Xmx1024m",  "-jar", "simulator.jar"
      , "--simul-start" , "20190101"
      , "--simul-end" , "20190831"
      , "--symbol" , "XBTUSD"
      , "--exchange" , "BITMEX"
      , "--mode" , "SHORT"
      , "--quote-currency" , "XBt"
      , "--strategy" , "VB"
      ,"--bb-input" , sys.argv[1]]

run_result = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
stdout, strerr = run_result.communicate()

print (stdout.decode('ascii').strip())
# print (stdout.decode('ascii').split('\n')[-2])
