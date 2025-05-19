import openai

def get_response(prompt):
    openai.api_key = "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"

    response = openai.ChatCompletion.create(
        model="gpt-3.5-turbo",
        messages=[
            {"role": "user", "content": prompt}
        ]
    )

    return response['choices'][0]['message']['content']