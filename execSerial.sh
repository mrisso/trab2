#!/bin/bash

echo "Tamanho,Tempo" > serial.csv
echo "" > err.log

for i in $(seq 1000 9000 100000); do
	echo -n "TAMANHO: $i"
	SAIDA=$(java -jar master.jar $i.crp noise)
	if [[ $SAIDA == *"encontradas!"* ]]
	then
		TEMPO=$(echo $SAIDA | sed "s/[^0-9]//g")
		echo $i,$TEMPO >> serial.csv
		echo -e "\t[OK]"
	else
		echo -e "\t[ERR]"
		echo $SAIDA >> "err.log"
	fi
done
