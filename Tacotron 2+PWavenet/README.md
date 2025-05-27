# Tacotron-2 + PWavenet:
Tensorflow implementation of Deep mind's Tacotron-2. A deep neural network architecture described in this paper: [Natural TTS synthesis by conditioning Wavenet on MEL spectogram predictions](https://arxiv.org/pdf/1712.05884.pdf)



The previous tree shows what the current state of the repository.

- Step **(0)**: Get your dataset, here I have set the examples of **Ljspeech**, **en_US** and **en_UK** (from **M-AILABS**).
- Step **(1)**: Preprocess your data. This will give you the **training_data** folder.
- Step **(2)**: Train your Tacotron model. Yields the **logs-Tacotron** folder.
- Step **(3)**: Synthesize/Evaluate the Tacotron model. Gives the **tacotron_output** folder.


Note:
- **Our preprocessing only supports Ljspeech and Ljspeech-like datasets!** If running on datasets stored differently, you will probably need to make your own preprocessing script.
- In the previous tree, files **were not represented** and **max depth was set to 3** for simplicity.

# Model Architecture:
<p align="center">
  <img src="https://preview.ibb.co/bU8sLS/Tacotron_2_Architecture.png"/>
</p>

The model described by the authors can be divided in two parts:
- Spectrogram prediction network
- Wavenet vocoder

# References and Resources:
- [Tensorflow original tacotron implementation](https://github.com/keithito/tacotron)
- [Original tacotron paper](https://arxiv.org/pdf/1703.10135.pdf)
- [Attention-Based Models for Speech Recognition](https://arxiv.org/pdf/1506.07503.pdf)
- [Natural TTS synthesis by conditioning Wavenet on MEL spectogram predictions](https://arxiv.org/pdf/1712.05884.pdf)
- [r9y9/wavenet_vocoder](https://github.com/r9y9/wavenet_vocoder)


