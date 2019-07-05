import threading
import random
from enum import Enum
from gpiozero import LED, Button
from signal import pause
from socket import socket, AF_INET, SOCK_DGRAM
from time import sleep

class Lights(Enum):
    NONE = 0x00
    GREEN = 0x01
    AMBER = 0x02
    AMBER_GREEN = 0x03
    RED = 0x04
    RED_GREEN = 0x05
    RED_AMBER= 0x06
    ALL = 0x07

class TrafficLight:
    def __init__(self, red_pin, amber_pin, green_pin):
        self._red = LED(red_pin)
        self._amber = LED(amber_pin)
        self._green = LED(green_pin)

    def set(self, lights):
        v = lights.value
        self._red.value = v & 0x04
        self._amber.value = v & 0x02
        self._green.value = v & 0x01

class FixedMode:
    def __init__(self, traffic_light, lights):
        self._traffic_light = traffic_light
        self._lights = lights
    
    def enter(self):
        self._traffic_light.set(self._lights)

    def exit(self):
        pass

class SequenceMode:
    def __init__(self, traffic_light, lights_intervals):
        self._traffic_light = traffic_light
        self._on_activated = threading.Event()
        self._lights_intervals_iterator = iter(lights_intervals)
        self._thread = threading.Thread(target=self._thread_start)
        self._thread.start()

    def _thread_start(self):
        while(self._on_activated.wait()):
            lights, interval = next(self._lights_intervals_iterator)
            self._traffic_light.set(lights)
            sleep(interval)

    def enter(self):
        self._on_activated.set()

    def exit(self):
        self._on_activated.clear()        

class UdpListenerMode:
    def __init__(self, traffic_light, address, port, update_timeout):
        self._traffic_light = traffic_light
        self._lights = Lights.AMBER
        self._is_active = False
        self._is_active_lock = threading.Lock()
        self._update_timeout = update_timeout
        self._on_updated = threading.Event()
        self._sock = socket(AF_INET, SOCK_DGRAM)
        self._sock.bind((address, port))
        self._listener_thread = threading.Thread(target=self._listener_thread_start)
        self._listener_thread.start()
        self._update_timeout_thread = threading.Thread(target=self._update_timeout_thread_start)
        self._update_timeout_thread.start()
        
    def _listener_thread_start(self):
        while True:
            msg, addr = self._sock.recvfrom(1024)
            if Lights.NONE.value <= msg[0] <= Lights.ALL.value:
                self._update(Lights(msg[0]))
                self._on_updated.set()

    def _update_timeout_thread_start(self):
        while True:
            if self._on_updated.wait(self._update_timeout):
                self._on_updated.clear()
            else:
                self._update(Lights.AMBER)
                self._on_updated.wait()

    def _update(self, lights):
        self._lights = lights
        with self._is_active_lock:
            if self._is_active:
                self._traffic_light.set(self._lights)

    def enter(self):
        self._traffic_light.set(self._lights)
        with self._is_active_lock:
            self._is_active = True

    def exit(self):
        with self._is_active_lock:
            self._is_active = False

class ModesController:
    def __init__(self, button, modes):
        self._i = 0
        self._modes = modes
        self._button = button

        def switch_mode():
            self._modes[self._i].exit()
            self._i = (self._i+1) % len(self._modes)
            self._modes[self._i].enter()
        self._button.when_pressed = switch_mode
        
    def start(self):
        self._modes[self._i].enter()

def looped_sequence(l):
    i = 0
    while True:
        yield l[i]
        i = (i+1) % len(l) 

def random_sequence_lights():
    while True:
        yield (Lights(random.randrange(len(Lights))), random.uniform(0.1, 0.5))

if __name__ == "__main__":
    traffic_light = TrafficLight(14, 15, 18)
    modes = [
                UdpListenerMode(traffic_light, '', 2806, 16.0),
                SequenceMode(traffic_light, random_sequence_lights()),
                SequenceMode(traffic_light, looped_sequence(list(map(lambda x: (x, 0.5), Lights)))),
                SequenceMode(traffic_light, looped_sequence([
                    (Lights.RED, 5.0),
                    (Lights.RED_AMBER, 1.5),
                    (Lights.GREEN, 5.0),
                    (Lights.NONE, 0.75),
                    (Lights.GREEN, 0.75),
                    (Lights.NONE, 0.75),
                    (Lights.GREEN, 0.75),
                    (Lights.NONE, 0.75),
                    (Lights.GREEN, 0.75),
                    (Lights.AMBER, 1.5),
                ])),
                FixedMode(traffic_light, Lights.ALL),
                FixedMode(traffic_light, Lights.NONE)
            ]
    controller = ModesController(Button(23), modes)
    controller.start()

    pause()
