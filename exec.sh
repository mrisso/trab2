#!/bin/bash

if [ $# -ne 1 ]
then
	echo "Uso: $0 <arquivo-saida>"
	echo "OBS.: A saída é salva no arquivo especificado em formato CSV"
	exit 1
fi

ARQUIVO=$1

echo "Tamanho,Tempo" > $ARQUIVO
echo "" > "err.log"

for i in $(seq 1000 9000 100000); do
	echo -n "TAMANHO: $i"
	SAIDA=$(java -jar client.jar $i.crp noise $i)
	if [[ $SAIDA == *"encontradas!"* ]]
	then
		TEMPO=$(echo $SAIDA | sed "s/[^0-9]//g")
		echo $i,$TEMPO >> $ARQUIVO
		echo -e "\t[OK]"
	else
		echo -e "\t[ERR]"
		echo $SAIDA >> "err.log"
	fi
done
