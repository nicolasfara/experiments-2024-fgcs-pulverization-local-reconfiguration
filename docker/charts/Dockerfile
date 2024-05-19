FROM python:3.12.3
RUN mkdir /experiment
WORKDIR /experiment
COPY .python-version ./
COPY process.py ./
COPY requirements.txt ./
RUN pip install --upgrade pip
RUN pip install -r requirements.txt
RUN sudo apt install texlive-latex-extra texlive-fonts-recommended dvipng cm-super
CMD python process.py
