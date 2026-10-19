package com.mindup.caiu_sos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

// Recebe dois avisos do Android: "o celular terminou de ligar" e "o app foi atualizado".
// Nos dois casos o MonitorService parou; se a pessoa tinha deixado o monitoramento ligado,
// ele é ligado de novo (só a pessoa desliga, pelo switch ou pelo "Parar" da notificação).
public class InicioReceiver extends BroadcastReceiver {

    // Chamado pelo Android quando um dos avisos chega
    @Override
    public void onReceive(Context context, Intent intent) {
        String acao = intent.getAction();
        boolean ligou = Intent.ACTION_BOOT_COMPLETED.equals(acao);
        boolean atualizou = Intent.ACTION_MY_PACKAGE_REPLACED.equals(acao);
        if ((ligou || atualizou) && Prefs.isMonitoramentoAtivo(context)) {
            Log.i("CaiuSOS", "Religando o monitoramento depois de: " + acao);
            ContextCompat.startForegroundService(context, new Intent(context, MonitorService.class));
        }
    }
}
