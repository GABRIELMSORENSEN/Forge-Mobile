"""Offline microphone transcription using an already installed Whisper model."""
import json,sys
import sounddevice as sd
from faster_whisper import WhisperModel
audio=sd.rec(int(6*16000),samplerate=16000,channels=1,dtype='float32');sd.wait()
model=WhisperModel('tiny',device='cpu',compute_type='int8',local_files_only=True)
segments,_=model.transcribe(audio[:,0],language='pt',beam_size=3)
print(json.dumps({'transcript':' '.join(s.text.strip() for s in segments)},ensure_ascii=True))
