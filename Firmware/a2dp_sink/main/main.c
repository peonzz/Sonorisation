/*
 * SPDX-FileCopyrightText: 2021-2023 Espressif Systems (Shanghai) CO LTD
 *
 * SPDX-License-Identifier: Unlicense OR CC0-1.0
 */

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <inttypes.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "nvs.h"
#include "nvs_flash.h"
#include "esp_system.h"
#include "esp_log.h"
#include "driver/gpio.h"
/* fixing %d format error */
#include <inttypes.h>

#include "esp_bt.h"
#include "bt_app_core.h"
#include "bt_app_av.h"
#include "esp_bt_main.h"
#include "esp_bt_device.h"
#include "esp_gap_bt_api.h"
#include "esp_a2dp_api.h"
#include "esp_avrc_api.h"
#include "esp_spp_api.h"

/* SPP for data exchange */
#define SPP_TAG "SPP_SERVER"
#define SPP_SERVER_NAME "SPP_SERVER"
#define UUID 0x001F

/* ADC */
#define NO_OF_SAMPLES 100
#define SLOPE 1

/* device name */
#define LOCAL_DEVICE_NAME    "Speakers"

/* gpio */
#define GPIO_OUTPUT_IO    2 
#define GPIO_OUTPUT_PIN_SEL  (1ULL<<GPIO_OUTPUT_IO)

/* event for stack up */
enum {
    BT_APP_EVT_STACK_UP = 0,
};

/* persistent data, first boot will handle configurations*/
//RTC_DATA_ATTR static int boot_count = 0;

/* light sleep task handler */
extern TaskHandle_t light_sleep_task_hdl;

/* esp spp mode for data exchange */
//static const esp_spp_mode_t esp_spp_mode = ESP_SPP_MODE_CB;

/* ADC */
// static const adc_channel_t channel = ADC_CHANNEL_6;     // GPIO34 if ADC1, GPIO14 if ADC2
// static const adc_atten_t atten = ADC_ATTEN_DB_11;
// static const adc_unit_t unit = ADC_UNIT_1;

/********************************
 * STATIC FUNCTION DECLARATIONS
 *******************************/

/* Init GPIO*/
static void gpio_init();
/* GAP callback function */
static void bt_app_gap_cb(esp_bt_gap_cb_event_t event, esp_bt_gap_cb_param_t *param);
/* handler for bluetooth stack enabled events */
static void bt_av_hdl_stack_evt(uint16_t event, void *p_param);
/* handler for data request from bluetooth */
static void esp_spp_cb(esp_spp_cb_event_t event, esp_spp_cb_param_t *param);


/*******************************
 * STATIC FUNCTION DEFINITIONS
 ******************************/


static void gpio_init() {
    gpio_config_t io_conf;
    /* deactivate interrupt */
    io_conf.intr_type = GPIO_INTR_DISABLE;
    /* set as an output */
    io_conf.mode = GPIO_MODE_OUTPUT;
    /* pin's configuration bit */
    io_conf.pin_bit_mask = GPIO_OUTPUT_PIN_SEL;
    /* deactivate pulldown */
    io_conf.pull_down_en = GPIO_PULLDOWN_DISABLE;
    /* deactivate pullup */
    io_conf.pull_up_en = GPIO_PULLUP_DISABLE;
    /* gpio config */
    gpio_config(&io_conf);
}

// static void double get_battery_level(){
//     uint32_t adc_reading = 0;
//     /* Multisampling */
//     for (int i = 0; i < NO_OF_SAMPLES; i++) {
//         adc_reading += adc1_get_raw((adc1_channel_t)channel);
//     }
//     adc_reading /= NO_OF_SAMPLES;
//     /* Convert adc_reading to voltage in mV */
//     uint32_t voltage = esp_adc_cal_raw_to_voltage(adc_reading, adc_chars);
// }

/* Bluetooth SPP callback */
/* SPP is a simple bluetooth profile. 
   SPP defines the requirements for Bluetooth devices necessary for setting up emulated serial cable connections using RFCOMM between two peer devices. */
static void esp_spp_cb(esp_spp_cb_event_t event, esp_spp_cb_param_t *param){
    switch (event) {
        case ESP_SPP_INIT_EVT:
            ESP_LOGI(SPP_TAG, "ESP_SPP_INIT_EVT");
            esp_spp_start_srv(ESP_SPP_SEC_AUTHENTICATE, ESP_SPP_ROLE_SLAVE, 0, SPP_SERVER_NAME);
            break;
        case ESP_SPP_DATA_IND_EVT:
            ESP_LOGI(SPP_TAG, "ESP_SPP_DATA_IND_EVT len=%"PRIu16"%% handle=%"PRIu32"%%", param->data_ind.len, param->data_ind.handle);
            esp_log_buffer_hex("",param->data_ind.data,param->data_ind.len);
            printf("test");
            esp_spp_write(param->data_ind.handle, param->data_ind.len, param->data_ind.data);
            break;
        default:
            break;
    }
}

/* Bluetooth GAP callback */

static void bt_app_gap_cb(esp_bt_gap_cb_event_t event, esp_bt_gap_cb_param_t *param)
{
    uint8_t *bda = NULL;

    switch (event) {
    /* when authentication completed, this event comes */
    case ESP_BT_GAP_AUTH_CMPL_EVT: {
        if (param->auth_cmpl.stat == ESP_BT_STATUS_SUCCESS) {
            ESP_LOGI(BT_AV_TAG, "authentication success: %s", param->auth_cmpl.device_name);
            esp_log_buffer_hex(BT_AV_TAG, param->auth_cmpl.bda, ESP_BD_ADDR_LEN);
        } else {
            ESP_LOGE(BT_AV_TAG, "authentication failed, status: %"PRIu8"%%", param->auth_cmpl.stat);
        }
        ESP_LOGI(BT_AV_TAG, "link key type of current link is: %"PRIu8"%%", param->auth_cmpl.lk_type);
        break;
    }
    case ESP_BT_GAP_ENC_CHG_EVT: {
        char *str_enc[3] = {"OFF", "E0", "AES"};
        bda = (uint8_t *)param->enc_chg.bda;
        ESP_LOGI(BT_AV_TAG, "Encryption mode to [%02x:%02x:%02x:%02x:%02x:%02x] changed to %s",
                 bda[0], bda[1], bda[2], bda[3], bda[4], bda[5], str_enc[param->enc_chg.enc_mode]);
        break;
    }


/* For SSP Bluetooth, not used for this project */
#if (CONFIG_EXAMPLE_A2DP_SINK_SSP_ENABLED == true)
    /* when Security Simple Pairing user confirmation requested, this event comes */
    case ESP_BT_GAP_CFM_REQ_EVT:
        ESP_LOGI(BT_AV_TAG, "ESP_BT_GAP_CFM_REQ_EVT Please compare the numeric value: %"PRIu32, param->cfm_req.num_val);
        esp_bt_gap_ssp_confirm_reply(param->cfm_req.bda, true);
        break;
    /* when Security Simple Pairing passkey notified, this event comes */
    case ESP_BT_GAP_KEY_NOTIF_EVT:
        ESP_LOGI(BT_AV_TAG, "ESP_BT_GAP_KEY_NOTIF_EVT passkey: %"PRIu32, param->key_notif.passkey);
        break;
    /* when Security Simple Pairing passkey requested, this event comes */
    case ESP_BT_GAP_KEY_REQ_EVT:
        ESP_LOGI(BT_AV_TAG, "ESP_BT_GAP_KEY_REQ_EVT Please enter passkey!");
        break;
#endif

    /* when GAP mode changed, this event comes */
    case ESP_BT_GAP_MODE_CHG_EVT:
        ESP_LOGI(BT_AV_TAG, "ESP_BT_GAP_MODE_CHG_EVT mode: %"PRIu8"%%", param->mode_chg.mode);
        break;
    /* when ACL connection completed, this event comes */
    case ESP_BT_GAP_ACL_CONN_CMPL_STAT_EVT:
        bda = (uint8_t *)param->acl_conn_cmpl_stat.bda;
        ESP_LOGI(BT_AV_TAG, "ESP_BT_GAP_ACL_CONN_CMPL_STAT_EVT Connected to [%02x:%02x:%02x:%02x:%02x:%02x], status: 0x%x",
                 bda[0], bda[1], bda[2], bda[3], bda[4], bda[5], param->acl_conn_cmpl_stat.stat);
        break;
    /* when ACL disconnection completed, this event comes */
    case ESP_BT_GAP_ACL_DISCONN_CMPL_STAT_EVT:
        bda = (uint8_t *)param->acl_disconn_cmpl_stat.bda;
        ESP_LOGI(BT_AV_TAG, "ESP_BT_GAP_ACL_DISC_CMPL_STAT_EVT Disconnected from [%02x:%02x:%02x:%02x:%02x:%02x], reason: 0x%x",
                 bda[0], bda[1], bda[2], bda[3], bda[4], bda[5], param->acl_disconn_cmpl_stat.reason);
        break;
    /* others */
    default: {
        ESP_LOGI(BT_AV_TAG, "event: %"PRIu16"%%", event);
        break;
    }
    }
}

/* Bluetooth event handler */

static void bt_av_hdl_stack_evt(uint16_t event, void *p_param)
{
    ESP_LOGD(BT_AV_TAG, "%s event: %"PRIu16"%%", __func__, event);

    switch (event) {
    /* when do the stack up, this event comes */
    case BT_APP_EVT_STACK_UP: {
        esp_bt_dev_set_device_name(LOCAL_DEVICE_NAME);
        esp_bt_gap_register_callback(bt_app_gap_cb);

        assert(esp_avrc_ct_init() == ESP_OK);
        esp_avrc_ct_register_callback(bt_app_rc_ct_cb);
        assert(esp_avrc_tg_init() == ESP_OK);
        esp_avrc_tg_register_callback(bt_app_rc_tg_cb);

        esp_avrc_rn_evt_cap_mask_t evt_set = {0};
        esp_avrc_rn_evt_bit_mask_operation(ESP_AVRC_BIT_MASK_OP_SET, &evt_set, ESP_AVRC_RN_VOLUME_CHANGE);
        assert(esp_avrc_tg_set_rn_evt_cap(&evt_set) == ESP_OK);

        assert(esp_a2d_sink_init() == ESP_OK);
        esp_a2d_register_callback(&bt_app_a2d_cb);
        esp_a2d_sink_register_data_callback(bt_app_a2d_data_cb);

        /* Get the default value of the delay value */
        esp_a2d_sink_get_delay_value();

        /* set discoverable and connectable mode, wait to be connected */
        esp_bt_gap_set_scan_mode(ESP_BT_CONNECTABLE, ESP_BT_GENERAL_DISCOVERABLE);
        break;
    }
    /* others */
    default:
        ESP_LOGE(BT_AV_TAG, "%s unhandled event: %"PRIu16"%%", __func__, event);
        break;
    }
}


/*******************************
 * MAIN ENTRY POINT
 ******************************/

void app_main(void)
{
    /* initialize gpio */
    gpio_init();

    /* initialize NVS — it is used to store PHY calibration data */
    esp_err_t err = nvs_flash_init();
    if (err == ESP_ERR_NVS_NO_FREE_PAGES || err == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        err = nvs_flash_init();
    }
    ESP_ERROR_CHECK(err);

    /*
     * This example only uses the functions of Classical Bluetooth.
     * So release the controller memory for Bluetooth Low Energy.
     */
    ESP_ERROR_CHECK(esp_bt_controller_mem_release(ESP_BT_MODE_BLE));

    esp_bt_controller_config_t bt_cfg = BT_CONTROLLER_INIT_CONFIG_DEFAULT();
    if ((err = esp_bt_controller_init(&bt_cfg)) != ESP_OK) {
        ESP_LOGE(BT_AV_TAG, "%s initialize controller failed: %s", __func__, esp_err_to_name(err));
        return;
    }
    if ((err = esp_bt_controller_enable(ESP_BT_MODE_CLASSIC_BT)) != ESP_OK) {
        ESP_LOGE(BT_AV_TAG, "%s enable controller failed: %s", __func__, esp_err_to_name(err));
        return;
    }

    esp_bluedroid_config_t bluedroid_cfg = BT_BLUEDROID_INIT_CONFIG_DEFAULT();
#if (CONFIG_EXAMPLE_A2DP_SINK_SSP_ENABLED == false)
    bluedroid_cfg.ssp_en = false;
#endif
    if ((err = esp_bluedroid_init_with_cfg(&bluedroid_cfg)) != ESP_OK) {
        ESP_LOGE(BT_AV_TAG, "%s initialize bluedroid failed: %s", __func__, esp_err_to_name(err));
        return;
    }

    if ((err = esp_bluedroid_enable()) != ESP_OK) {
        ESP_LOGE(BT_AV_TAG, "%s enable bluedroid failed: %s", __func__, esp_err_to_name(err));
        return;
    }

    /* Configuration pour le réveil par signal Bluetooth */
    /*if((err = esp_sleep_pd_config(ESP_PD_DOMAIN_RTC_PERIPH, ESP_PD_OPTION_ON))) {
        ESP_LOGE(BT_AV_TAG, "%s enable bluetooth wakeup failed : %s", __func__, esp_err_to_name(err));
        return;
    }*/

#if (CONFIG_EXAMPLE_A2DP_SINK_SSP_ENABLED == true)
    /* set default parameters for Secure Simple Pairing */
    esp_bt_sp_param_t param_type = ESP_BT_SP_IOCAP_MODE;
    esp_bt_io_cap_t iocap = ESP_BT_IO_CAP_IO;
    esp_bt_gap_set_security_param(param_type, &iocap, sizeof(uint8_t));
#endif

    /* set default parameters for Legacy Pairing (use fixed pin code 1234) */
    esp_bt_pin_type_t pin_type = ESP_BT_PIN_TYPE_FIXED;
    esp_bt_pin_code_t pin_code;
    pin_code[0] = '1';
    pin_code[1] = '2';
    pin_code[2] = '3';
    pin_code[3] = '4';
    pin_code[4] = '5';
    pin_code[5] = '6';
    pin_code[6] = '7';
    pin_code[7] = '8';
    esp_bt_gap_set_pin(pin_type, 8, pin_code);

    bt_app_task_start_up();
    /* bluetooth device name, connection mode and profile set up */
    bt_app_work_dispatch(bt_av_hdl_stack_evt, BT_APP_EVT_STACK_UP, NULL, 0, NULL);

    //esp_spp_register_callback(esp_spp_cb);
    //esp_spp_enhanced_init(NULL);
    printf("Task");
    configure_light_sleep();
    xTaskCreate(light_sleep_task, "light_sleep_task", 4096, NULL, 1, &light_sleep_task_hdl);
}
