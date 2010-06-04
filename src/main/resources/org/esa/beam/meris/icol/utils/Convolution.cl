
__kernel void Convolve(const __global float* input,
                       const int width,
                       const int height,
                       const __constant float* filter,
                       const int filterSize,
                       __global float* output
)
{
    const int x = get_global_id(0);
    const int y = get_global_id(1);
    const int d = filterSize;

    float sum = 0;
    if (x >= d/2 && x < width - d/2 && y >= d/2 && y < height - d/2) {
        for (int j = 0; j < filterSize; j++) {
            const int idxFilter = j * filterSize;
            const int idxInput = ((y-d/2) + j) * width + (x-d/2);
            for (int i = 0; i < filterSize; i++) {
                sum += filter[idxFilter + i] * input[idxInput + i];
            }
        }
   }

   /* TODO: address edge processing problem: perform a 'partial' convolution with fraction of filter */

   const int idxOutput = y * width + x;
   output[idxOutput] = sum;
}
